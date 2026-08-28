# NitronBox Android AI Client

Production-oriented Android architecture for a deeply customizable, multi-provider AI workspace client. The project uses Kotlin, Jetpack Compose, Room, Paging 3, OkHttp streaming, kotlinx serialization, SAF, and Android Keystore. It targets Android API 35 while retaining `minSdk 26` compatibility.

## Visual direction: Liquid Glass

The UI is a native Compose interpretation of the iOS 26 spatial/Liquid Glass language—not a Material theme with reduced opacity:

- sampled superellipse (`SquircleShape`) continuous corners;
- layered translucent tint, highlights, fine borders, ambient gradients, and depth shadows;
- spring-scale press interactions and emphasized easing tokens;
- edge-to-edge floating header/composer and spatial message hierarchy;
- API 31+ Compose `BlurEffect` for decorative ambient depth;
- API 26–30 static radial gradients and translucent overlays without realtime blur;
- workspace-controlled palette, font source, scale, glass depth, and reduced motion.

Android cannot sample and blur arbitrary content behind a composable with the same private system compositor used by Apple. The implementation therefore blurs controlled ambient source layers and uses a performant glass material for interactive surfaces. This avoids repeated offscreen captures and old-device frame drops.

## Architecture

```text
UI (Compose + Paging)
  ├── ui/theme          Liquid Glass tokens, adaptive effects, typography
  └── ui/chat           chat surface and native Markdown block renderer
             │
Domain       ├── ContextWindowEngine / TokenEstimator / Summarizer
             │
Data         ├── Room + WAL + indexed PagingSource
             ├── AiProviderBridge + FailoverRouter
             ├── SAF attachment streaming
             └── Android Keystore credential vault
```

### Provider flow

1. Save a `ProviderProfile` containing protocol, endpoint, discovery method/path, and a **credential alias**.
2. Store the actual key with `SecureCredentialStore`; keys never enter Room or serialized workspace records.
3. Create `HttpAiProviderBridge(profile, credentialStore, httpClient)`.
4. `discoverModels()` executes the configured GET/POST and normalizes its response into `DiscoveredModel` values.
5. Save the opaque selected ID as a `ModelTarget`. No model catalog is hardcoded.
6. `FailoverRouter` tries the primary and ordered fallbacks. It will not switch after visible output, avoiding duplicate generations.

Built-in protocol mappings cover OpenAI-compatible APIs (OpenAI, DeepSeek, Groq, LocalAI, vLLM and compatible proxies), Anthropic, Gemini, and Ollama. Custom headers and POST discovery are supported. Cleartext endpoints are rejected except explicit loopback hosts; shipping local-LAN HTTP support requires a user-approved debug/network security policy.

### Conversation history

- Room uses foreign keys, cascade deletion, compound indexes and WAL.
- `NitronBoxDao.pagedMessages()` exposes newest-first `PagingSource` data.
- Paging is bounded to a 160-item in-memory window.
- Request context assembly is intentionally separate from UI paging. It preserves system/recent messages and supports prune, summary, or reject policies.
- `TokenEstimator` is replaceable with a JNI/JVM tokenizer for an exact model encoding; the default multilingual estimator is conservative.

### Attachments

`AttachmentPipeline` accepts TXT, PDF, CSV, JSON, JPEG, PNG and WebP through SAF. It retains URI permission, validates MIME and byte limits, hashes incrementally, streams multipart bodies, and bounds base64 conversion to prevent OOM. PDF text extraction/vector indexing belongs behind a background `WorkManager` ingestion boundary.

### Markdown

`MarkdownParser` turns content into stable block models. `MarkdownRenderer` produces native Compose nodes for headings, inline emphasis/strike/code/links, lists, nested quotes, tables and code containers with language, Copy and Wrap controls. LaTeX and charts are extension interfaces, allowing KaTeX/MathJax or Mermaid/Vega engines without coupling the chat list to WebView.

## Security checklist

- No keys in source control, Room, logs, exceptions, backups, or workspace exports.
- TLS is mandatory outside emulator/device loopback.
- Provider error bodies are truncated; production telemetry should redact payloads and headers.
- Validate redirect hosts if redirects are enabled for untrusted custom endpoints.
- For high-assurance deployments, add database encryption (for example SQLCipher), certificate pinning for managed official endpoints, Play Integrity, and biometric-gated credential use.
- Android Auto Backup is disabled in the manifest.

## What works end to end

- **Chat with real providers**: streaming generations through OpenAI-compatible APIs, Anthropic, Gemini, and Ollama, with failover, stop, retry/regenerate, and per-message token/error tracking persisted in Room.
- **Provider management**: Settings → Providers lets you add/edit providers (protocol, base URL, API key stored in the Android Keystore), test reachability, and load the live model catalog through each provider's discovery endpoint.
- **Model selection**: the chat header opens a model picker that discovers models on demand — no hardcoded catalog.
- **Conversations**: side drawer with workspace switching, conversation list, rename, delete, and lazy conversation creation on first send.
- **Workspace editor**: system prompt, generation settings (temperature, max output tokens), and context-window policy (budget, output reserve, overflow strategy incl. LLM-backed SUMMARIZE).
- **Attachments**: SAF file picking with hash/permission/size validation; text, CSV, JSON, and PDF (pdfbox) content is inlined into the request; images are sent as native multimodal parts for vision-capable protocols.
- **Markdown**: native renderer for headings, emphasis, links, ordered/unordered/task lists, nested quotes, tables, images (Coil), and code blocks with copy/wrap controls.

## Build

Use Android Studio with JDK 17 and Android SDK 35, or run:

```powershell
./gradlew.bat testDebugUnitTest assembleDebug
```

The generated APK is located at `app/build/outputs/apk/debug/app-debug.apk`.

## Recommended next production increments

1. Add an exact tokenizer behind `TokenEstimator` and per-provider model context limits.
2. Add vector store adapters and background ingestion workers for PDF indexing.
3. Add explicit Room migrations beyond v2, SQLCipher for regulated environments, baseline profiles, Macrobenchmark, accessibility and screenshot tests.