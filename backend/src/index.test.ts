import { describe, expect, it } from "vitest";
import app, { cleanFeedbackText, feedbackInstruction, normalizeFeedback } from "./index";
import type { Env } from "./types";

const env: Env = {
  OPENAI_API_KEY: "test-openai-key",
  APP_API_TOKEN: "test-token",
  ALLOWED_MODELS: "gpt-5.2,gpt-5-mini,gpt-5-nano",
  DEFAULT_MODEL: "gpt-5-mini"
};

describe("english car backend", () => {
  it("returns health without authentication", async () => {
    const response = await app.request("/health", {}, env);
    expect(response.status).toBe(200);
    expect(await response.json()).toMatchObject({ ok: true });
  });

  it("rejects private endpoints without token", async () => {
    const response = await app.request("/v1/models", {}, env);
    expect(response.status).toBe(401);
  });

  it("returns allowed models with token", async () => {
    const response = await app.request(
      "/v1/models",
      { headers: { Authorization: "Bearer test-token" } },
      env
    );
    expect(response.status).toBe(200);
    expect(await response.json()).toEqual({
      models: ["gpt-5.2", "gpt-5-mini", "gpt-5-nano"],
      defaultModel: "gpt-5-mini"
    });
  });

  it("cleans placeholder feedback values", () => {
    expect(cleanFeedbackText("none")).toBeNull();
    expect(cleanFeedbackText("N/A")).toBeNull();
    expect(cleanFeedbackText("ninguna")).toBeNull();
    expect(cleanFeedbackText("I went yesterday.")).toBe("I went yesterday.");
  });

  it("does not save feedback when every structured value is empty", () => {
    const normalized = normalizeFeedback(
      {
        spokenReply: "Nice. Tell me more.",
        correction: "none",
        naturalAlternative: "n/a",
        shortExplanation: null,
        shouldSaveFeedback: true
      },
      "medium"
    );

    expect(normalized).toEqual({
      spokenReply: "Nice. Tell me more.",
      correction: null,
      naturalAlternative: null,
      shortExplanation: null,
      shouldSaveFeedback: false
    });
  });

  it("forces spoken correction when feedback level is high", () => {
    const normalized = normalizeFeedback(
      {
        spokenReply: "What did you do after that?",
        correction: "I went yesterday.",
        naturalAlternative: null,
        shortExplanation: "Use past tense for yesterday.",
        shouldSaveFeedback: true
      },
      "high"
    );

    expect(normalized.spokenReply).toContain("Quick correction");
    expect(normalized.spokenReply).toContain("I went yesterday.");
    expect(normalized.shouldSaveFeedback).toBe(true);
  });

  it("builds distinct feedback instructions", () => {
    expect(feedbackInstruction("low")).toContain("Only correct important");
    expect(feedbackInstruction("medium")).toContain("Correction level: medium");
    expect(feedbackInstruction("high")).toContain("Mention every useful correction");
  });
});
