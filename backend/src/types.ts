export type Env = {
  OPENAI_API_KEY: string;
  APP_API_TOKEN: string;
  ALLOWED_MODELS: string;
  DEFAULT_MODEL: string;
};

export type AiModel = "gpt-5.2" | "gpt-5-mini" | "gpt-5-nano" | "gpt-4o-mini";
