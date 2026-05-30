export type Env = {
  GEMINI_API_KEY: string;
  APP_API_TOKEN: string;
  ALLOWED_MODELS: string;
  DEFAULT_MODEL: string;
};

export type AiModel = "gemini-2.5-flash" | "gemini-2.5-flash-lite" | "gemini-2.0-flash" | "gemini-2.0-flash-lite";
