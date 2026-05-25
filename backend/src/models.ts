import { z } from "zod";
import type { AiModel, Env } from "./types";

const knownModels = ["gpt-5.2", "gpt-5-mini", "gpt-5-nano", "gpt-4o-mini"] as const;

export const modelSchema = z.enum(knownModels);

export function getAllowedModels(env: Env): AiModel[] {
  const configured = env.ALLOWED_MODELS?.split(",").map((item) => item.trim()).filter(Boolean) ?? [];
  const valid = configured.filter((item): item is AiModel => knownModels.includes(item as AiModel));
  return valid.length > 0 ? valid : ["gpt-5-mini"];
}

export function getDefaultModel(env: Env): AiModel {
  const parsed = modelSchema.safeParse(env.DEFAULT_MODEL);
  return parsed.success ? parsed.data : "gpt-5-mini";
}
