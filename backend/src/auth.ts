import type { Context, Next } from "hono";
import type { Env } from "./types";

export async function requireAppToken(c: Context<{ Bindings: Env }>, next: Next) {
  const expected = c.env.APP_API_TOKEN;
  const header = c.req.header("Authorization");
  const token = header?.replace(/^Bearer\s+/i, "").trim();

  if (!expected || token !== expected) {
    return c.json({ error: "Unauthorized" }, 401);
  }

  await next();
}
