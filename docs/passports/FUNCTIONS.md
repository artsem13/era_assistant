# Era / Sphere capability registry

This is the single registry for capabilities. Add rows here before creating another passport. A separate passport is justified only when a subsystem has its own contracts, state, storage, dependencies and lifecycle.

## Status vocabulary

`PLANNED` — no implementation. `IMPLEMENTED` — code exists; device behavior is not claimed. `DEVICE_PASS` — code and real device evidence exist. `DISABLED` — intentionally unavailable. `DEPRECATED` — retained only for compatibility/history.

## Confirmed and active capabilities

| ID / logical name | What it does | Executor / adapter | Dependencies and permissions | Input | Result | Status | Portability / security notes |
|---|---|---|---|---|---|---|---|
| `get_current_location` | Gets one current location fix | `TermuxExecutor` → fixed worker → `termux-location` | Termux, Termux:API, RUN_COMMAND permission, location permission/configuration in Termux | No arbitrary arguments | `LocationCapabilityResult` with validated coordinates and optional accuracy/provider/etc. | `DEVICE_PASS` | Termux-specific adapter; no coordinates in docs; bounded JSON; no shell input |
| `open_2gis_route` | Opens a car route from an origin to a destination | Android-native provider adapter `TwoGisRouteCapability` | Installed 2GIS package `ru.dublgis.dgismobile`; Android activity resolution | Typed `RouteOrigin`, `RouteDestination` | `RouteCapabilityResult` (`OPENED`, `UNAVAILABLE`, `INVALID_DESTINATION`, `FAILED`) and fixed deep link | `DEVICE_PASS` | Provider/device dependency isolated from core; coordinates are typed and range-validated |
| `termux_runtime_info` | Returns bounded local Termux runtime metadata | `TermuxExecutor` → fixed worker | Termux RUN_COMMAND permission and installed worker | No arguments | Bounded text result | `DEVICE_PASS` | Read-only, allowlisted; not arbitrary shell |
| `termux_lifecycle_probe` | Debug-only fixed heartbeat/cancellation diagnostic | Termux fixed worker, debug Activity | Debug build, Termux and worker | No arguments | Bounded status/result/heartbeat diagnostics | `IMPLEMENTED` | Device confirmation remains pending; never a production shell/API |
| Conversation response | Sends a user text/voice message through OpenAI and stores the response | OpenAI client/streaming path | OpenAI API-key URI, network | User text and conversation context | Assistant text plus local archive/usage updates | `IMPLEMENTED` | API/provider dependency; secrets stay outside repository |
| Voice Mode | Half-duplex capture → batch STT → OpenAI response → TTS | Voice controllers and xAI STT/TTS integrations | `RECORD_AUDIO`, network, device audio route | User speech | Transcript, assistant response and playback state | `IMPLEMENTED` | See `VOICE.md`; device audio behavior remains device-specific |
| Internet search evidence | Routes current/social queries to xAI tools and adds bounded evidence to OpenAI context | `SearchOrchestrator` / `XaiSearchClient` | xAI API-key URI, network | Query and search mode | Evidence bundle, citations, raw app-private record, usage counters | `IMPLEMENTED` | See `SEARCH.md`; no Codex backend |
| Long-term memory | Compiles eligible RAW blocks into topics/items and retrieves relevant context | Memory stores/compiler/retriever | Local SQLite, OpenAI API key for compiler/embeddings | Archived conversation | Memory context and compiler run state | `IMPLEMENTED` | See `MEMORY.md`; approximate token estimate and async processing |

## Capability contracts

Typed inputs must be validated before the adapter. Results must be bounded and explicit about unavailable, failed, cancelled and completed states. No capability may accept a universal shell command, arbitrary executable path, arbitrary package or arbitrary URI as a substitute for a typed contract.

## Pending / disabled directions

There is no current Codex capability. Era → Codex, local/remote Codex executor, Codex orchestration, PRoot → Codex security host and new Codex probes are `DEPRECATED`/historical, not `PLANNED`. New capabilities should be added as registry rows and assigned to the smallest suitable adapter.

## Required verification

Static: inspect the typed contract and adapter, then run targeted read-back and `git diff --check`. Device claims require a real device test. Record portability, permission and security boundaries in the row; do not record personal coordinates or secrets.
