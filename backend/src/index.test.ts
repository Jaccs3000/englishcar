import { describe, expect, it } from "vitest";
import app, { cleanFeedbackText, normalizeFeedback, normalizeTranscriptText } from "./index";

const env = {
  GEMINI_API_KEY: "",
  APP_API_TOKEN: "test-token",
  ALLOWED_MODELS: "gemini-2.5-flash-lite,gemini-2.0-flash-lite,gemini-2.0-flash,gemini-2.5-flash",
  DEFAULT_MODEL: "gemini-2.5-flash-lite"
};

describe("english car gemini backend", () => {
  it("returns health capabilities", async () => {
    const response = await app.request("/health", {}, env);
    expect(response.status).toBe(200);
    const body = await response.json() as { aiProvider: string; capabilities: { transcribe: boolean } };
    expect(body.aiProvider).toBe("google-gemini");
    expect(body.capabilities.transcribe).toBe(true);
  });

  it("requires token for models", async () => {
    const response = await app.request("/v1/models", {}, env);
    expect(response.status).toBe(401);
  });

  it("returns gemini model allowlist", async () => {
    const response = await app.request("/v1/models", { headers: { Authorization: "Bearer test-token" } }, env);
    expect(response.status).toBe(200);
    const body = await response.json() as { models: string[]; defaultModel: string };
    expect(body.models[0]).toBe("gemini-2.5-flash-lite");
    expect(body.defaultModel).toBe("gemini-2.5-flash-lite");
  });

  it("normalizes correction placeholders", () => {
    expect(cleanFeedbackText("none")).toBeNull();
    expect(cleanFeedbackText("I went yesterday.")).toBe("I went yesterday.");
  });

  it("adds a required correction prefix when feedback exists", () => {
    const normalized = normalizeFeedback({
      spokenReply: "Good. Tell me more.",
      correction: "I went yesterday.",
      naturalAlternative: null,
      shortExplanation: null,
      shouldSaveFeedback: false
    });
    expect(normalized.spokenReply).toContain("You should say:");
    expect(normalized.shouldSaveFeedback).toBe(true);
  });

  it("ignores punctuation-only question mark feedback", () => {
    const normalized = normalizeFeedback({
      spokenReply: "You should say: What time is it? Good question.",
      correction: "What time is it?",
      naturalAlternative: null,
      shortExplanation: "Missing question mark.",
      shouldSaveFeedback: true
    }, "what time is it");
    expect(normalized.correction).toBeNull();
    expect(normalized.shortExplanation).toBeNull();
    expect(normalized.shouldSaveFeedback).toBe(false);
  });

  it("normalizes Gemini transcription JSON shapes", () => {
    expect(normalizeTranscriptText("\"I am ready.\"")).toBe("I am ready.");
    expect(normalizeTranscriptText("{\"text\":\"I went home.\"}")).toBe("I went home.");
  });
});
