import { z } from "zod";
import type { AiModel, Env } from "./types";

const knownModels = [
  "gemini-2.5-flash-lite",
  "gemini-2.0-flash-lite",
  "gemini-2.0-flash",
  "gemini-2.5-flash"
] as const;

export const modelSchema = z.enum(knownModels);

export function getAllowedModels(env: Env): AiModel[] {
  const configured = env.ALLOWED_MODELS?.split(",").map((item) => item.trim()).filter(Boolean) ?? [];
  const valid = configured.filter((item): item is AiModel => knownModels.includes(item as AiModel));
  return valid.length > 0 ? valid : ["gemini-2.5-flash-lite"];
}

export function getDefaultModel(env: Env): AiModel {
  const parsed = modelSchema.safeParse(env.DEFAULT_MODEL);
  return parsed.success ? parsed.data : "gemini-2.5-flash-lite";
}
