import { Hono } from "hono";
import OpenAI from "openai";
import { z } from "zod";
import { requireAppToken } from "./auth";
import { getAllowedModels, getDefaultModel, modelSchema } from "./models";
import type { Env } from "./types";

const app = new Hono<{ Bindings: Env }>();

const contextItemSchema = z.object({
  role: z.enum(["user", "assistant"]),
  text: z.string().min(1).max(1200)
});

const conversationRequestSchema = z.object({
  requestId: z.string().min(1).max(120),
  type: z.enum(["start_conversation", "conversation_turn"]),
  userText: z.string().max(2000).nullable().optional(),
  assistantId: z.string().min(1).max(40),
  assistantName: z.string().min(1).max(80),
  assistantPersonality: z.string().min(1).max(80),
  userName: z.string().max(80).nullable().optional(),
  model: modelSchema,
  feedbackLevel: z.enum(["low", "medium", "high"]).default("medium"),
  locale: z.literal("en-US"),
  recentContext: z.array(contextItemSchema).max(20)
});

app.get("/health", (c) => {
  return c.json({
    ok: true,
    service: "english-car-backend",
    provider: "cloudflare-workers"
  });
});

app.use("/v1/*", requireAppToken);

app.get("/v1/models", (c) => {
  return c.json({
    models: getAllowedModels(c.env),
    defaultModel: getDefaultModel(c.env)
  });
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

function createConversationStream(env: Env, request: ConversationRequest): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder();

  return new ReadableStream({
    async start(controller) {
      try {
        const finalResponse = env.OPENAI_API_KEY
          ? await callOpenAi(env, request)
          : buildFallbackResponse(request);

        enqueueSse(controller, encoder, "delta", { text: finalResponse.spokenReply });
        enqueueSse(controller, encoder, "final", finalResponse);
      } catch (error) {
        enqueueSse(controller, encoder, "error", {
          message: error instanceof Error ? error.message : "Unknown backend error"
        });
      } finally {
        controller.close();
      }
    }
  });
}

async function callOpenAi(env: Env, request: ConversationRequest): Promise<AiFinalResponse> {
  try {
    return await callOpenAiWithModel(env, request, request.model);
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    if (request.model.startsWith("gpt-5") && message.toLowerCase().includes("organization must be verified")) {
      return callOpenAiWithModel(env, request, "gpt-4o-mini");
    }
    throw error;
  }
}

async function callOpenAiWithModel(
  env: Env,
  request: ConversationRequest,
  model: string
): Promise<AiFinalResponse> {
  const client = new OpenAI({ apiKey: env.OPENAI_API_KEY });
  const prompt = buildPrompt(request);

  const response = await client.responses.create({
    model,
    input: prompt,
    text: {
      format: {
        type: "json_schema",
        name: "english_car_response",
        strict: true,
        schema: {
          type: "object",
          additionalProperties: false,
          required: [
            "spokenReply",
            "correction",
            "naturalAlternative",
            "shortExplanation",
            "shouldSaveFeedback"
          ],
          properties: {
            spokenReply: { type: "string" },
            correction: { anyOf: [{ type: "string" }, { type: "null" }] },
            naturalAlternative: { anyOf: [{ type: "string" }, { type: "null" }] },
            shortExplanation: { anyOf: [{ type: "string" }, { type: "null" }] },
            shouldSaveFeedback: { type: "boolean" }
          }
        }
      }
    }
  });

  const output = response.output_text;
  const parsed = aiFinalResponseSchema.safeParse(JSON.parse(output));
  if (!parsed.success) {
    throw new Error("OpenAI returned an invalid response shape.");
  }

  const parsedResponse = parsed.data;
  const normalized = normalizeFeedback(parsedResponse, request.feedbackLevel);
  return {
    ...normalized,
    shouldSaveFeedback: normalized.shouldSaveFeedback ||
      Boolean(normalized.correction || normalized.naturalAlternative || normalized.shortExplanation)
  };
}

export function normalizeFeedback(response: AiFinalResponse, feedbackLevel: ConversationRequest["feedbackLevel"]): AiFinalResponse {
  const correction = cleanFeedbackText(response.correction);
  const naturalAlternative = cleanFeedbackText(response.naturalAlternative);
  const shortExplanation = cleanFeedbackText(response.shortExplanation);
  const hasFeedback = Boolean(correction || naturalAlternative || shortExplanation);
  let spokenReply = response.spokenReply.trim();

  if (!hasFeedback) {
    return {
      spokenReply,
      correction: null,
      naturalAlternative: null,
      shortExplanation: null,
      shouldSaveFeedback: false
    };
  }

  if (feedbackLevel === "high" && !mentionsFeedback(spokenReply, correction, naturalAlternative)) {
    const spokenCorrection = correction || naturalAlternative;
    spokenReply = `Quick correction: say "${spokenCorrection}". ${spokenReply}`;
  }

  return {
    spokenReply,
    correction,
    naturalAlternative,
    shortExplanation,
    shouldSaveFeedback: response.shouldSaveFeedback || hasFeedback
  };
}

export function cleanFeedbackText(value: string | null): string | null {
  const cleaned = value?.trim();
  if (!cleaned) return null;
  const normalized = cleaned.toLowerCase().replace(/[.:\-\s]+$/g, "").trim();
  if (["none", "no", "n/a", "na", "null", "ninguna", "ninguno", "no correction", "no corrections"].includes(normalized)) {
    return null;
  }
  return cleaned;
}

function mentionsFeedback(spokenReply: string, correction: string | null, naturalAlternative: string | null): boolean {
  const lower = spokenReply.toLowerCase();
  return [correction, naturalAlternative].some((value) => value && lower.includes(value.toLowerCase()));
}

function buildPrompt(request: ConversationRequest): string {
  const context = request.recentContext
    .map((item) => `${item.role}: ${item.text}`)
    .join("\n");

  if (request.type === "start_conversation") {
    return [
      "You are an American English conversation coach for a hands-free driving app.",
      `Assistant name: ${request.assistantName}.`,
      `Assistant personality: ${request.assistantPersonality}.`,
      request.userName ? `User name: ${request.userName}.` : "User name: unknown.",
      "Start a short, natural American English conversation.",
      "Use 1-2 short sentences.",
      "Do not mention app features."
    ].join("\n");
  }

  return [
    "You are an American English conversation coach for a hands-free driving app.",
    "Use American English only.",
    "Conversation first, teaching second.",
    "Reply with 1-3 short spoken sentences.",
    feedbackInstruction(request.feedbackLevel),
    "Never fill correction, naturalAlternative, or shortExplanation with 'none', 'n/a', 'no', or similar placeholder text. Use null when there is no useful feedback.",
    "If you correct grammar, suggest a natural alternative, or explain an improvement, set shouldSaveFeedback to true.",
    "When shouldSaveFeedback is true, fill at least one of correction, naturalAlternative, or shortExplanation with real useful content.",
    "After any correction or suggestion, continue the conversation naturally.",
    "Do not give long explanations.",
    `Assistant name: ${request.assistantName}.`,
    `Assistant personality: ${request.assistantPersonality}.`,
    request.userName ? `User name: ${request.userName}. Mention it only occasionally.` : "User name: unknown.",
    context ? `Recent context:\n${context}` : "Recent context: none.",
    `User said: ${request.userText ?? ""}`
  ].join("\n");
}

export function feedbackInstruction(level: ConversationRequest["feedbackLevel"]): string {
  if (level === "low") {
    return "Correction level: low. Only correct important grammar, meaning, or very unnatural phrasing. If feedback is minor, set all feedback fields to null and keep the conversation moving.";
  }
  if (level === "high") {
    return "Correction level: high. Mention every useful correction or natural alternative briefly in spokenReply, and save it in the structured feedback fields.";
  }
  return "Correction level: medium. Correct important mistakes and useful natural alternatives briefly. Minor polish can be saved only when it helps the user sound more natural.";
}

function buildFallbackResponse(request: ConversationRequest): AiFinalResponse {
  const spokenReply = request.type === "start_conversation"
    ? `Hi${request.userName ? `, ${request.userName}` : ""}. I'm ${request.assistantName}. Let's practice natural American English.`
    : `I heard: ${request.userText ?? ""}. Nice. Tell me a little more about that.`;

  return {
    spokenReply,
    correction: null,
    naturalAlternative: null,
    shortExplanation: null,
    shouldSaveFeedback: false
  };
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
