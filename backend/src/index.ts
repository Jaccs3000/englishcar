import { Hono } from "hono";
import { z } from "zod";
import { requireAppToken } from "./auth";
import { getAllowedModels, getDefaultModel, modelSchema } from "./models";
import type { Env } from "./types";

const app = new Hono<{ Bindings: Env }>();

const contextItemSchema = z.object({
  role: z.enum(["user", "assistant"]),
  text: z.string().min(1).max(1200)
});

const correctionPhrases = [
  "You should say:",
  "A better way to say that is:",
  "The correct form is:",
  "More natural to say:",
  "You can say:",
  "The correct pronunciation is:",
  "Instead of saying [my phrase], say:"
];

const conversationRequestSchema = z.object({
  requestId: z.string().min(1).max(120),
  type: z.enum(["start_conversation", "conversation_turn"]),
  userText: z.string().max(4000).nullable().optional(),
  assistantId: z.enum(["female", "male"]),
  assistantName: z.string().min(1).max(80),
  assistantPersonality: z.string().min(1).max(120),
  userName: z.string().max(80).nullable().optional(),
  model: modelSchema,
  locale: z.literal("en-US"),
  recentContext: z.array(contextItemSchema).max(20)
});

type ConversationRequest = z.infer<typeof conversationRequestSchema>;

type AiFinalResponse = {
  spokenReply: string;
  correction: string | null;
  naturalAlternative: string | null;
  shortExplanation: string | null;
  shouldSaveFeedback: boolean;
};

const aiFinalResponseSchema = z.object({
  spokenReply: z.string().min(1),
  correction: z.string().nullable(),
  naturalAlternative: z.string().nullable(),
  shortExplanation: z.string().nullable(),
  shouldSaveFeedback: z.boolean()
});

app.get("/health", (c) => {
  return c.json({
    ok: true,
    service: "english-car-backend",
    provider: "cloudflare-workers",
    aiProvider: "google-gemini",
    version: "gemini-stable-voice-1",
    capabilities: {
      conversationStream: true,
      transcribe: true
    }
  });
});

app.use("/v1/*", requireAppToken);

app.get("/v1/models", (c) => {
  return c.json({
    models: getAllowedModels(c.env),
    defaultModel: getDefaultModel(c.env)
  });
});

app.post("/v1/transcribe", async (c) => {
  const form = await c.req.formData().catch(() => null);
  const audio = form?.get("audio");

  if (!audio || typeof audio !== "object" || !("arrayBuffer" in audio) || !("size" in audio)) {
    return c.json({ error: "Audio file is required" }, 400);
  }

  const audioFile = audio as unknown as File;
  if (audioFile.size < 800) return c.json({ text: "" });

  try {
    const audioBase64 = arrayBufferToBase64(await audioFile.arrayBuffer());
    const response = await callGemini(c.env, getDefaultModel(c.env), [
      {
        role: "user",
        parts: [
          {
            text: [
              "Transcribe this audio as American English.",
              "Return JSON only with this shape: {\"text\":\"exact words spoken\"}.",
              "If the speech is empty or not understandable, return {\"text\":\"\"}."
            ].join(" ")
          },
          { inlineData: { mimeType: audioFile.type || "audio/wav", data: audioBase64 } }
        ]
      }
    ]);
    return c.json({ text: normalizeTranscriptText(extractGeminiText(response)) });
  } catch (error) {
    console.warn("transcribe.error", { detail: safeThrowableDetails(error) });
    return c.json({ error: "Could not transcribe audio", code: "gemini_transcribe_error" }, 503);
  }
});

app.post("/v1/conversation/stream", async (c) => {
  const body = await c.req.json().catch(() => null);
  const parsed = conversationRequestSchema.safeParse(body);

  if (!parsed.success) {
    return c.json({ error: "Invalid request", details: parsed.error.flatten() }, 400);
  }

  const allowedModels = getAllowedModels(c.env);
  if (!allowedModels.includes(parsed.data.model)) {
    return c.json({ error: "Model is not allowed" }, 400);
  }

  const stream = createConversationStream(c.env, parsed.data);
  return new Response(stream, {
    headers: {
      "Content-Type": "text/event-stream; charset=utf-8",
      "Cache-Control": "no-cache",
      "Connection": "keep-alive"
    }
  });
});

function createConversationStream(env: Env, request: ConversationRequest): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder();

  return new ReadableStream({
    async start(controller) {
      try {
        const finalResponse = env.GEMINI_API_KEY
          ? await callGeminiConversation(env, request)
          : buildFallbackResponse(request);

        enqueueSse(controller, encoder, "delta", { text: finalResponse.spokenReply });
        enqueueSse(controller, encoder, "final", finalResponse);
      } catch (error) {
        const detail = safeThrowableDetails(error);
        console.warn("conversation.error", { detail });
        enqueueSse(controller, encoder, "error", {
          code: "conversation_response_error",
          message: "The assistant is not available right now.",
          details: detail
        });
      } finally {
        controller.close();
      }
    }
  });
}

async function callGeminiConversation(env: Env, request: ConversationRequest): Promise<AiFinalResponse> {
  const prompt = buildPrompt(request);
  const response = await callGemini(env, request.model, [{ role: "user", parts: [{ text: prompt }] }]);
  const text = extractGeminiText(response);
  const parsedJson = parseJsonObject(text);
  const parsed = aiFinalResponseSchema.safeParse(parsedJson);
  if (!parsed.success) throw new Error("Gemini returned an invalid response shape.");
  return normalizeFeedback(parsed.data, request.userText ?? "");
}

async function callGemini(env: Env, model: string, contents: unknown[]) {
  if (!env.GEMINI_API_KEY) throw new Error("Gemini API key is not configured.");
  const response = await fetch(
    `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${env.GEMINI_API_KEY}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        contents,
        generationConfig: {
          temperature: 0.35,
          responseMimeType: "application/json"
        }
      })
    }
  );
  const text = await response.text();
  if (!response.ok) throw new Error(`Gemini error ${response.status}: ${text.slice(0, 180)}`);
  return JSON.parse(text);
}

function extractGeminiText(response: unknown): string {
  const candidates = (response as { candidates?: Array<{ content?: { parts?: Array<{ text?: string }> } }> }).candidates ?? [];
  return candidates.flatMap((candidate) => candidate.content?.parts ?? []).map((part) => part.text ?? "").join("").trim();
}

function parseJsonObject(text: string): unknown {
  const trimmed = text.trim().replace(/^```json\s*/i, "").replace(/^```\s*/i, "").replace(/```$/i, "").trim();
  return JSON.parse(trimmed);
}

export function normalizeTranscriptText(text: string): string {
  const trimmed = text.trim();
  if (!trimmed) return "";
  try {
    const parsed = JSON.parse(trimmed) as unknown;
    if (typeof parsed === "string") return parsed.trim();
    if (parsed && typeof parsed === "object" && "text" in parsed) {
      const value = (parsed as { text?: unknown }).text;
      return typeof value === "string" ? value.trim() : "";
    }
    return trimmed;
  } catch {
    return trimmed;
  }
}

export function normalizeFeedback(response: AiFinalResponse, userText = ""): AiFinalResponse {
  let correction = cleanFeedbackText(response.correction);
  let naturalAlternative = cleanFeedbackText(response.naturalAlternative);
  let shortExplanation = cleanFeedbackText(response.shortExplanation);
  if (isTrivialFeedback(userText, correction, naturalAlternative, shortExplanation)) {
    correction = null;
    naturalAlternative = null;
    shortExplanation = null;
  }
  const hasFeedback = Boolean(correction || naturalAlternative || shortExplanation);
  let spokenReply = response.spokenReply.trim();

  if (hasFeedback && !correctionPhrases.some((phrase) => spokenReply.toLowerCase().includes(phrase.toLowerCase().replace("[my phrase]", "").trim()))) {
    const spokenCorrection = correction || naturalAlternative;
    spokenReply = `You should say: ${spokenCorrection}. ${spokenReply}`;
  }

  return {
    spokenReply: spokenReply || "Can you repeat, please?",
    correction,
    naturalAlternative,
    shortExplanation,
    shouldSaveFeedback: hasFeedback
  };
}

export function cleanFeedbackText(value: string | null): string | null {
  const cleaned = value?.trim();
  if (!cleaned) return null;
  const normalized = cleaned.toLowerCase().replace(/[.:\-\s]+$/g, "").trim();
  if (["none", "no", "n/a", "na", "null", "ninguna", "ninguno", "no correction", "no corrections"].includes(normalized)) return null;
  return cleaned;
}

function isTrivialFeedback(
  userText: string,
  correction: string | null,
  naturalAlternative: string | null,
  shortExplanation: string | null
): boolean {
  const original = normalizeMeaningText(userText);
  if (!original) return false;
  const candidates = [correction, naturalAlternative].filter((value): value is string => Boolean(value));
  if (candidates.length === 0) return false;
  const onlyPunctuationOrCase = candidates.every((value) => normalizeMeaningText(value) === original);
  if (!onlyPunctuationOrCase) return false;
  const explanation = shortExplanation?.toLowerCase() ?? "";
  const trivialExplanation = !explanation || ["punctuation", "question mark", "capitalization", "comma", "period", "signo"].some((word) => explanation.includes(word));
  return trivialExplanation;
}

function normalizeMeaningText(value: string): string {
  return value
    .toLowerCase()
    .replace(/\b(my phrase|say|instead of saying|you should say|the correct form is|a better way to say that is|more natural to say|you can say)\b/g, " ")
    .replace(/["'`´“”‘’.,!?¿¡:;()[\]{}\-_/\\]+/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function buildPrompt(request: ConversationRequest): string {
  if (request.type === "start_conversation") {
    return JSON.stringify({
      task: "Return JSON only.",
      schema: {
        spokenReply: "string",
        correction: null,
        naturalAlternative: null,
        shortExplanation: null,
        shouldSaveFeedback: false
      },
      instruction: "The assistant starts by saying exactly: I'm ready",
      assistantName: request.assistantName,
      userName: request.userName
    });
  }

  const context = request.recentContext.map((item) => `${item.role}: ${item.text}`).join("\n");
  return [
    "You are an American English conversation coach for a hands-free driving app.",
    "Return JSON only with keys spokenReply, correction, naturalAlternative, shortExplanation, shouldSaveFeedback.",
    "The assistant must be didactic, neutral, paused, easy to understand, and always use American English.",
    "Never interrupt the user; answer only after the user has finished.",
    "If the user text is empty or unclear, spokenReply must be exactly: Can you repeat, please?",
    "Correct only real grammar, word-order, vocabulary, meaning, or pronunciation problems.",
    "Do not correct punctuation, capitalization, commas, periods, or a missing question mark when the spoken word order is already correct.",
    "Do not save feedback when the only difference is punctuation or written formatting.",
    "If there is meaningful feedback, spokenReply must begin with one of these exact phrases:",
    correctionPhrases.join(" | "),
    "Keep spokenReply brief: correction plus one short follow-up question when useful.",
    "shortExplanation may be null when performance matters; correction and naturalAlternative are more important.",
    `Assistant name: ${request.assistantName}.`,
    `Assistant voice style: ${request.assistantPersonality}.`,
    request.userName ? `User name: ${request.userName}.` : "User name: unknown.",
    context ? `Recent context:\n${context}` : "Recent context: none.",
    `User said: ${request.userText ?? ""}`
  ].join("\n");
}

function buildFallbackResponse(request: ConversationRequest): AiFinalResponse {
  return {
    spokenReply: request.type === "start_conversation" ? "I'm ready" : "Can you repeat, please?",
    correction: null,
    naturalAlternative: null,
    shortExplanation: null,
    shouldSaveFeedback: false
  };
}

function arrayBufferToBase64(buffer: ArrayBuffer): string {
  let binary = "";
  const bytes = new Uint8Array(buffer);
  for (let index = 0; index < bytes.length; index += 1) binary += String.fromCharCode(bytes[index]);
  return btoa(binary);
}

function safeThrowableDetails(error: unknown): string {
  if (!(error instanceof Error)) return "unknown_error";
  return [error.name, error.message.slice(0, 180)].filter(Boolean).join(": ");
}

function enqueueSse(
  controller: ReadableStreamDefaultController<Uint8Array>,
  encoder: TextEncoder,
  event: string,
  data: unknown
) {
  controller.enqueue(encoder.encode(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`));
}

export default app;
