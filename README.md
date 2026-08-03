# Prism AI

Mobile-first AI chat with a liquid glass interface, Markdown rendering and BYOK connections.

## Providers

- OpenAI
- Anthropic
- Google Gemini
- OpenRouter
- Groq
- Mistral AI
- xAI
- Any OpenAI-compatible custom endpoint

## Run

```bash
npm install
npm run dev
```

Open `http://localhost:5173`. Production build:

```bash
npm run build
npm start
```

The production server opens on `http://127.0.0.1:8788`.

## Android

The Android application uses Capacitor's native HTTP transport, so provider APIs work directly in the APK without a local web server. Build with `npm run android:apk` when Android SDK is installed, or use the included GitHub Actions workflow.

API keys are kept only in the current tab memory and are never written to local storage. Chat history and non-secret settings are stored locally in the browser.
