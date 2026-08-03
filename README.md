# NitronBox

Native Android AI chat built with Kotlin and Jetpack Compose.

## Stack

- Kotlin
- Jetpack Compose and Material 3
- OkHttp with native SSE streaming
- Native OpenAI, Anthropic and Gemini protocol adapters
- No JavaScript, React, WebView, Capacitor or local server

## Providers

NitronBox loads the current model catalog directly from each provider API:

- OpenAI
- Anthropic
- Google Gemini
- OpenRouter
- Groq
- Mistral AI
- xAI
- Custom OpenAI-compatible provider

## Build

Open the `android` directory in Android Studio or run:

```bash
cd android
./gradlew assembleDebug
```

API keys are kept in application memory and are not persisted.
