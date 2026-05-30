const config = {
  startArmingDelayMs: 800,
  minStartCandidateMs: 64,
  minUtteranceMs: 1600,
  silenceLimitMs: 1500,
  speechStartThreshold: 380,
  speechContinueThreshold: 260,
  peakStartThreshold: 1500,
  peakStartMinAverage: 240
};

function repeat(level, count, peak = level * 4) {
  return Array.from({ length: count }, () => ({ level, peak, ms: 64 }));
}

function simulate(name, chunks) {
  let listeningMs = 0;
  let startCandidateMs = 0;
  let hasSpeech = false;
  let speechMs = 0;
  let silenceMs = 0;
  let voicedMs = 0;
  let peakLevel = 0;
  const events = [];

  for (const { level, peak = level * 4, ms = 64 } of chunks) {
    listeningMs += ms;
    peakLevel = Math.max(peakLevel, level);
    const isStartCandidate =
      listeningMs >= config.startArmingDelayMs &&
      (level > config.speechStartThreshold ||
        (peak > config.peakStartThreshold && level > config.peakStartMinAverage));
    const continuesSpeech = level > config.speechContinueThreshold;

    if (!hasSpeech && isStartCandidate) {
      startCandidateMs += ms;
    } else if (!hasSpeech) {
      startCandidateMs = 0;
    }

    if (!hasSpeech && startCandidateMs >= config.minStartCandidateMs) {
      hasSpeech = true;
      silenceMs = 0;
      events.push(`start@${listeningMs} level=${level} peak=${peak}`);
    } else if (hasSpeech && continuesSpeech) {
      silenceMs = 0;
    } else if (hasSpeech) {
      silenceMs += ms;
    }

    if (hasSpeech) {
      speechMs += ms;
      if (continuesSpeech) voicedMs += ms;
    }

    if (hasSpeech && speechMs >= config.minUtteranceMs && silenceMs >= config.silenceLimitMs) {
      events.push(`complete@${listeningMs} speechMs=${speechMs} silenceMs=${silenceMs} voicedMs=${voicedMs} peak=${peakLevel}`);
      return { name, detected: true, events };
    }
  }

  return { name, detected: false, events, final: { hasSpeech, speechMs, silenceMs, voicedMs, peakLevel, listeningMs } };
}

const cases = [
  {
    name: "missed soft phrase from physical log",
    expectDetected: true,
    chunks: [
      ...repeat(160, 14, 600),
      { level: 510, peak: 1875 },
      { level: 413, peak: 1300 },
      ...repeat(180, 24, 600)
    ]
  },
  {
    name: "later ignored speech peaks from physical log",
    expectDetected: true,
    chunks: [
      ...repeat(180, 14, 650),
      { level: 655, peak: 2758 },
      { level: 533, peak: 1800 },
      { level: 943, peak: 2832 },
      { level: 535, peak: 1600 },
      ...repeat(251, 12, 984),
      ...repeat(150, 25, 500)
    ]
  },
  {
    name: "clear 3 second phrase",
    expectDetected: true,
    chunks: [
      ...repeat(140, 14, 500),
      ...repeat(600, 8, 2200),
      ...repeat(330, 30, 1200),
      ...repeat(150, 25, 500)
    ]
  },
  {
    name: "steady car rumble",
    expectDetected: false,
    chunks: repeat(280, 80, 1000)
  }
];

const results = cases.map((testCase) => ({
  ...simulate(testCase.name, testCase.chunks),
  expectDetected: testCase.expectDetected
}));

for (const result of results) {
  const pass = result.detected === result.expectDetected;
  console.log(`${pass ? "PASS" : "FAIL"} ${result.name}`);
  console.log(JSON.stringify(result, null, 2));
}

if (results.some((result) => result.detected !== result.expectDetected)) {
  process.exit(1);
}
