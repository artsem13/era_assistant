# Era / Sphere — current project state

**Snapshot:** 2026-08-19. **Authority:** repository code and Git; this document is a compact verified snapshot, not a roadmap.

## Purpose

Era is a local Android assistant and Sphere interaction surface. It accepts text and voice intent, keeps conversation and long-term memory locally, optionally obtains bounded external evidence, and exposes narrowly typed device capabilities. The core is not coupled to a particular executor.

## Current architecture

```text
Sphere intent
  → typed capability / core service
  → validation and policy boundary
  → Android-native or provider adapter, or TermuxExecutor
  → bounded result / UI state
```

OpenAI remains the main conversation/model path. xAI search is an optional evidence path whose result is appended to the OpenAI instruction path. Executors are adapters, not model backends.

**Codex integration: REMOVED FROM TARGET ARCHITECTURE.** Era does not invoke Codex, a local Codex sandbox, a remote Codex executor, or Codex orchestration. Old Codex documents are archive-only.

## Main modules and screens

- `MainActivity` is the UI composition point. It wires OpenAI conversation, archive, memory, search status, voice mode and navigation; feature logic lives in controllers/providers.
- Core AI: OpenAI client/streaming response and usage calculation; xAI search/STT clients are separate integrations.
- Core memory: `ConversationArchive`, RAW blocks, compiler runs, memory items/topics, embeddings and retrieval.
- Core search: decision routing, `XaiSearchClient`, response parsing, `SearchRawArchive` and `SearchUsageTracker`.
- Core voice: half-duplex Voice Mode, manual mic/STT and TTS controllers.
- Screens present in the main manifest include Main, Usage, Black Box and keyguard test activities. Accessibility and Android Voice Interaction services are registered; the debug build adds `TermuxDeviceTestActivity`.

## Storage and data architecture

- `era_conversation_archive.db` is the local SQLite archive. It contains `messages` and `research_notes`; memory components add `raw_blocks`, compiler-run, memory-item/topic and embedding tables to the same archive connection.
- `era_preferences` SharedPreferences stores API-key URI, model/usage counters, search counters and other local settings.
- xAI raw evidence is stored under app-private `filesDir/xai_search_raw` after secret scanning. Authorization headers and API keys are not written to those records.
- Local backup is triggered by archive/memory writes through `LocalMemoryBackup`; the exact backup/restore behavior remains a migration concern, not a device-independent guarantee.
- Android permissions, URI grants, enabled services, Termux task files and OEM settings are device state, not portable Era data. Export/import is not implemented.

## Active executors and confirmed capabilities

- `TermuxExecutor` implements the neutral `ExternalExecutor` contract through the fixed worker and Android `RUN_COMMAND` transport. It is optional and trusted only for allowlisted typed capabilities.
- `TwoGisRouteCapability` is a provider-specific Android adapter using a fixed package and deep link.
- `AndroidLocationCapability` is an Android-native location adapter; its device status is not claimed here unless separately tested.
- Device-confirmed: `get_current_location` completed with valid latitude/longitude, accuracy and `provider=gps` through Termux. `open_2gis_route` opened 2GIS with a route from the current location. No user coordinates are stored in documentation.

Termux is **not** arbitrary shell, a filesystem sandbox, or a guaranteed durable background host. The current TECNO/HiOS phone is a temporary host, not architecture.

## Known limitations and unfinished areas

- Termux worker deployment is separate from the APK; background execution and callback delivery depend on Termux, permissions and OEM lifecycle behavior.
- Termux task state is private executor state and is not portable Era memory. No durable cross-device task migration exists.
- Voice remains half-duplex with built-in MIC as the production baseline; Bluetooth input and automatic acoustic barge-in are postponed/disabled per Voice passport.
- Search and xAI usage require a user-provided API-key URI, network access and human/device verification for the current implementation.
- Memory compilation and embeddings are asynchronous and model/network dependent; approximate RAW token estimation is not an API tokenizer.
- Export/import, a universal shell capability, and any Codex executor are not implemented and are not target directions.
- Debug-only lifecycle probing remains distinct from production capabilities; its device confirmation is tracked in the Termux passport.

## Architectural decisions

- Neutral typed contracts precede executor choice; a new capability must validate input and use fixed adapter behavior.
- No universal command, executable path, package or arbitrary URI is passed to a low-level executor.
- MainActivity remains wiring-only; new business logic belongs in small classes/adapters/controllers.
- TECNO/HiOS quirks belong in diagnostics or a device adapter, never in portable core.

## Required verification for state changes

Use targeted source read-back and `git diff --check`. Device claims require an actual device test; an implementation is not a `DEVICE_PASS` until such evidence exists.
