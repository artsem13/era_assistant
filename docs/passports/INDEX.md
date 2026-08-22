# Era / Sphere documentation map

This directory is the authoritative documentation layer for the current repository state. Code and Git are the primary facts; passports summarize verified code contracts and device evidence.

## Current map

| Document | Responsibility |
|---|---|
| [PROJECT_STATE.md](PROJECT_STATE.md) | Current project snapshot: architecture, modules, storage, devices, limitations and decisions |
| [FUNCTIONS.md](FUNCTIONS.md) | Registry of typed capabilities and functional areas; the default home for new capabilities |
| [TERMUX_BRIDGE.md](TERMUX_BRIDGE.md) | Current Era ↔ Termux transport, worker protocol, lifecycle and security boundary |
| [VOICE.md](VOICE.md) | Large, independently maintained Voice subsystem contract |
| [MEMORY.md](MEMORY.md) | Local RAW/archive and long-term memory contract |
| [SEARCH.md](SEARCH.md) | xAI search routing, evidence archive and usage contract |
| [USAGE.md](USAGE.md) | OpenAI/xAI usage UI and local counters |
| [INTERFACE.md](INTERFACE.md) | Актуальный паспорт UI/UX пользовательского интерфейса Era |
| [DIAGNOSTICS.md](DIAGNOSTICS.md) | Локальный диагностический журнал и экспорт |

Read this file first, then only the passport relevant to the task. Do not treat audits, plans or reports as current instructions.

## Architectural line

`Sphere intent → typed capability → policy → concrete executor/adapter → bounded result`.

Executors are replaceable. Termux is the current trusted local capability executor; Android-native and provider-specific adapters are separate options. Codex is not an Era executor/backend and is removed from the target architecture.

## Where historical material lives

Historical audits, research, implementation plans and superseded passports are under [`docs/archive/`](../archive/). They are evidence only. In particular, all Era → Codex / PRoot → Codex material is historical; it must not be used to design current work.

## Task routing

- Project or architecture decision: `PROJECT_STATE.md`.
- New or changed capability: `FUNCTIONS.md`; use a separate passport only when the subsystem has its own state, contract, storage, dependencies and lifecycle.
- Termux, worker, callback, task state or deployment: `TERMUX_BRIDGE.md`.
- Voice/audio/STT/TTS: `VOICE.md`.
- RAW archive, memory compiler, embeddings or local memory: `MEMORY.md`.
- Search, citations, xAI raw evidence or search counters: `SEARCH.md` and `USAGE.md`.

## Document labels

`AUTHORITATIVE` means current repository guidance. `MERGE_INTO_PASSPORT` means facts have been absorbed here. `HISTORICAL_AUDIT` and `ARCHIVE` mean useful evidence only. `DUPLICATE`, `OBSOLETE` and `DELETE_CANDIDATE` are review labels; no uncertain document is deleted automatically. The detailed disposition is in [`docs/archive/README.md`](../archive/README.md).
