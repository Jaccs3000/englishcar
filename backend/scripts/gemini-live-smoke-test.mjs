import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { GoogleGenAI, Modality } from "@google/genai";

const rootDir = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const env = loadEnvFile(resolve(rootDir, ".env.local"));
const apiKey = env.GEMINI_API_KEY || process.env.GEMINI_API_KEY;
const model = env.GEMINI_LIVE_MODEL || process.env.GEMINI_LIVE_MODEL || "gemini-3.1-flash-live-preview";
const voiceName = env.GEMINI_LIVE_VOICE || process.env.GEMINI_LIVE_VOICE || "Kore";

if (!apiKey || apiKey === "PASTE_YOUR_KEY_HERE") {
  throw new Error("Set GEMINI_API_KEY in backend/.env.local before running this test.");
}

const outputPath = resolve(rootDir, "..", "tmp", "gemini-live-output.wav");
const prompt = [
  "You are Isa, an American English coach for a hands-free driving app.",
  "Correct only clear English mistakes.",
  "Keep your spoken response short, natural, and didactic.",
  "User says: I am ready. Can you listen to me?"
].join(" ");

const ai = new GoogleGenAI({ apiKey });
const responseQueue = [];
const audioChunks = [];
const startedAt = Date.now();
let firstAudioAt = 0;
let firstTextAt = 0;
let closed = false;

const config = {
  responseModalities: [Modality.AUDIO],
  outputAudioTranscription: {},
  speechConfig: {
    voiceConfig: {
      prebuiltVoiceConfig: { voiceName }
    }
  },
  thinkingConfig: {
    thinkingLevel: "minimal"
  },
  realtimeInputConfig: {
    automaticActivityDetection: {
      disabled: false,
      prefixPaddingMs: 20,
      silenceDurationMs: 250
    }
  }
};

const session = await ai.live.connect({
  model,
  config,
  callbacks: {
    onopen() {
      console.log(`Gemini Live opened model=${model} voice=${voiceName}`);
    },
    onmessage(message) {
      responseQueue.push(message);
    },
    onerror(error) {
      console.error("Gemini Live error:", error?.message || error);
    },
    onclose(event) {
      closed = true;
      console.log("Gemini Live closed:", event?.reason || "");
    }
  }
});

session.sendRealtimeInput({ text: prompt });

const timeoutAt = Date.now() + 30_000;
let turnComplete = false;
while (!turnComplete && Date.now() < timeoutAt) {
  const message = responseQueue.shift();
  if (!message) {
    await sleep(30);
    continue;
  }

  const content = message.serverContent;
  const outputText = content?.outputTranscription?.text;
  if (outputText) {
    if (!firstTextAt) firstTextAt = Date.now();
    console.log("output transcript:", outputText);
  }

  const inputText = content?.inputTranscription?.text;
  if (inputText) console.log("input transcript:", inputText);

  const parts = content?.modelTurn?.parts || [];
  for (const part of parts) {
    const audioData = part.inlineData?.data;
    if (audioData) {
      if (!firstAudioAt) firstAudioAt = Date.now();
      audioChunks.push(Buffer.from(audioData, "base64"));
    }
  }

  if (content?.interrupted) console.log("generation interrupted");
  if (content?.turnComplete) turnComplete = true;
}

session.close();

if (!turnComplete) {
  throw new Error(`Gemini Live did not complete a turn before timeout. closed=${closed}`);
}

const pcm = Buffer.concat(audioChunks);
if (!pcm.length) {
  throw new Error("Gemini Live completed but returned no audio chunks.");
}

mkdirSync(dirname(outputPath), { recursive: true });
writeFileSync(outputPath, wavFromPcm16(pcm, 24_000));

console.log(`firstTextMs=${firstTextAt ? firstTextAt - startedAt : "none"}`);
console.log(`firstAudioMs=${firstAudioAt ? firstAudioAt - startedAt : "none"}`);
console.log(`audioBytes=${pcm.length}`);
console.log(`saved=${outputPath}`);

function loadEnvFile(path) {
  try {
    return Object.fromEntries(
      readFileSync(path, "utf8")
        .split(/\r?\n/)
        .map((line) => line.trim())
        .filter((line) => line && !line.startsWith("#") && line.includes("="))
        .map((line) => {
          const index = line.indexOf("=");
          return [line.slice(0, index).trim(), line.slice(index + 1).trim()];
        })
    );
  } catch {
    return {};
  }
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function wavFromPcm16(pcm, sampleRate) {
  const header = Buffer.alloc(44);
  const byteRate = sampleRate * 2;
  header.write("RIFF", 0);
  header.writeUInt32LE(36 + pcm.length, 4);
  header.write("WAVE", 8);
  header.write("fmt ", 12);
  header.writeUInt32LE(16, 16);
  header.writeUInt16LE(1, 20);
  header.writeUInt16LE(1, 22);
  header.writeUInt32LE(sampleRate, 24);
  header.writeUInt32LE(byteRate, 28);
  header.writeUInt16LE(2, 32);
  header.writeUInt16LE(16, 34);
  header.write("data", 36);
  header.writeUInt32LE(pcm.length, 40);
  return Buffer.concat([header, pcm]);
}
