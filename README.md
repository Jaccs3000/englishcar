# English Car

Personal Android and Android Auto American English voice coach.

## Project layout

```text
android/   Android app
backend/   Cloudflare Workers backend
plan.md    Product and execution specification
```

## Verification

### Backend

```powershell
cd C:\englishcar\backend
npm install
npm run typecheck
npm test
```

Expected:

- TypeScript typecheck passes.
- Vitest tests pass.
- `/health` is public.
- `/v1/models` rejects requests without `Authorization: Bearer <APP_API_TOKEN>`.

For local Worker development, create `backend\.dev.vars` from `.dev.vars.example`:

```text
OPENAI_API_KEY=your_openai_key
APP_API_TOKEN=your_personal_token
```

Then run:

```powershell
npm run dev
```

### Android

Open this folder in Android Studio:

```text
C:\englishcar\android
```

Then sync Gradle and run the `app` configuration on an emulator or physical device.

Command-line build:

```powershell
cd C:\englishcar\android
.\gradlew.bat :app:assembleDebug
```

Expected:

- App opens to quick setup on first launch.
- Home shows PLAY, Feedback, and Settings.
- Settings lets you configure assistants, model, timeouts, commands, Worker URL, and APP API token.
- Pressing PLAY starts a voice conversation.
- The backend proxies OpenAI requests securely.
- Feedback history stores useful corrections locally.
- Android Auto template service exposes Start, Pause/Resume, and Finish controls.
