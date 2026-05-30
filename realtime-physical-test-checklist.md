# English Car realtime physical test checklist

Use this checklist after installing the debug APK on the phone.

## Logcat filters

```text
EnglishCarRealtime|EnglishCarAudioTrack|EnglishCarConversation|EnglishCarService|EnglishCarBackendClient
```

Do not share backend tokens or OpenAI keys in logs or screenshots.

## Stable mode

1. Set conversation mode to `Stable`.
2. Start a conversation.
3. Confirm voice input, backend response, Google TTS, Pause, Resume, Finish and Exit work.
4. Lock the phone during Listening and Speaking.
5. Expected result: current stable behavior remains unchanged.

## Realtime mode

1. Set conversation mode to `Realtime experimental`.
2. Start a conversation on Wi-Fi.
3. Confirm logs show client secret, websocket upgrade, microphone streaming and AudioTrack playback.
4. Interrupt the assistant while it is speaking.
5. Expected result: assistant audio stops quickly and the app listens to the new phrase.

## Mobile network

1. Repeat realtime mode on mobile data.
2. Drive or move through weak signal if possible.
3. Toggle airplane mode for 5-10 seconds, then restore data.
4. Expected result: app attempts realtime reconnect twice, then falls back to stable mode without crashing.

## Background and lock screen

1. Start realtime mode.
2. Lock the phone.
3. Use notification actions: Pause, Resume, Finish.
4. Expected result: foreground service remains active and audio/microphone stop cleanly on Finish.

## Bluetooth and Android Auto

1. Start realtime mode with Bluetooth audio connected.
2. Disconnect and reconnect Bluetooth.
3. Test Android Auto Start, Pause, Resume and Finish.
4. Expected result: controls use the same conversation state and do not leave the microphone running after Finish.

## Failure notes

Record:

- visible app state;
- exact Logcat lines around the first error;
- whether fallback happened;
- whether microphone or audio stayed active after Finish;
- network type: Wi-Fi, mobile data, Bluetooth, Android Auto.
