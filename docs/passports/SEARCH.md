# Search subsystem passport

## Current State

WEB search is implemented and device-validated for the current phase. The first local decision layer is the RuBERT-tiny2 WEB Router, running on Android through ONNX Runtime. RuBERT only routes; it does not answer the user or execute network search.

The current flow is:

```text
user message
  → local RuBERT WEB Router
  → AUTO_NO_WEB: ordinary conversation path, without network WEB
  → AUTO_WEB: existing SearchIntentParser → xAI WEB pipeline
  → MINI_FALLBACK: GPT-5 Mini with recent conversation context
       → WEB + self-contained query → existing SearchIntentParser → xAI WEB pipeline
       → NO_WEB: ordinary conversation path
       → CLARIFY_USER: short clarification in the ordinary chat
       → selected main OpenAI Sphere model
       → natural final answer to the user
```

The policy is fixed for this phase: `p_web <= 0.20` → `AUTO_NO_WEB`; `p_web >= 0.80` → `AUTO_WEB`; values between the thresholds → `MINI_FALLBACK`.

`SearchOrchestrator` uses `SearchDecisionController` to select `NO_SEARCH`, `GENERAL_WEB`, `SOCIAL_REALTIME_X` or `BOTH`. `XaiSearchClient` calls `https://api.x.ai/v1/responses` with model `grok-4.3`, low reasoning effort, sequential bounded tool calls and a 2 MiB response limit. Parsed evidence is appended to the existing OpenAI instruction path; OpenAI remains the final conversation response path.

After `AUTO_WEB` or Mini `WEB`, `SearchIntentParser` is a separate task making a separate OpenAI Responses API call using `gpt-5-mini`. Its payload contains only the original user query and `SearchMode`; it does not receive conversation history, RAW memory blocks, structured memory, Sphere instructions or previous search evidence. Its separate instructions and JSON schema normalize the natural-language request into a bounded query, intent, required facts and optional location. The normalized query becomes the xAI input. The original query is retained in Search RAW alongside the actual query sent to xAI. Parser latency is stored as `intent_parse_ms`.

For `MINI_FALLBACK`, `MiniWebDecisionResolver` uses the existing `OpenAiClient` with model `gpt-5-mini`, strict structured JSON output and a bounded recent context from `ConversationArchive` (six latest previous messages, maximum 2400 characters). Its only outputs are `WEB` with a self-contained `search_query`, `NO_WEB`, or `CLARIFY_USER` with a short question. It is called only after RuBERT returns `MINI_FALLBACK`. Mini does not answer the main user question and does not access memory routing.

`MemoryCompiler` and `SearchIntentParser` happen to use the same `gpt-5-mini` model, but are logically and API-architecturally independent: they have separate API calls, instructions, payloads, schemas, parsing, lifecycle and state. The search parser is not combined with memory processing and is not a shared mini-processing request.

If the parser has no OpenAI key, times out, has a network/API failure, returns invalid JSON, an empty query or fails validation, `SearchOrchestrator` calls xAI with the original user query. Search therefore remains available when normalization fails. The parser is never called for `NO_SEARCH`.

Explicit user commands such as searching/checking in the internet or network, and requests to find/check current information or data, are handled locally by the RuBERT WEB layer when confidence is high. Explicit search negation has priority in the Mini policy and in the legacy fallback. Current-time words alone are insufficient for search. `SearchIntentParser` is not called for `AUTO_NO_WEB`, Mini `NO_WEB` or `CLARIFY_USER`.

`SearchRawArchive` writes request/response evidence to app-private `filesDir/xai_search_raw` only after secret scanning. API keys and authorization headers are not stored. `XaiSearchResponseParser` retains answer text, sources/citations, usage, latency and response identity. `SearchUsageTracker` persists xAI request/token/tool/latency/tick counters in `era_preferences` and `UsageActivity` displays provider-specific usage.

Before the final OpenAI request, `EvidenceBundle.toOpenAiContext()` wraps the complete bounded evidence as internal reference material and adds the user-facing response contract: synthesize from the evidence in the model's own words, omit citation/source/URL artifacts by default, and use plain text. Citation and source data remain in `EvidenceBundle` and the raw archive; they are not removed from the search subsystem. If the user explicitly requests sources or links, the contract leaves that response possible.

The evidence is internal material for the main model. The main model must synthesize the answer in its own words, without copying the evidence structure; by default it must not expose raw URLs, source lists, `[[1]]`/`[1]`-style citation syntax, search-pipeline details or Markdown markers such as `**...**` in the ordinary answer. URLs, citations and raw evidence remain available internally for diagnostics and a future source interface.

Routing covers explicit commands to search/check the internet or current information, plus objectively current external-data requests according to the implemented heuristics. This passport does not broaden that routing policy.

### Accepted routing architecture

The accepted routing cascade has one local decision layer and one bounded ambiguity resolver:

```text
user message
  → local RuBERT-tiny2 web router (first level)
      → explicit WEB: execute web search
      → explicit NO_WEB: continue ordinary conversation
      → uncertain: GPT-5 Mini with recent context
          → WEB + self-contained query
          → NO_WEB
          → CLARIFY_USER
```

RuBERT is a routing component only: it uses the frozen local runtime, classifier and fixed `0.20/0.80` thresholds. `AUTO_WEB` enters the existing `SearchIntentParser → XaiSearchClient` pipeline; `AUTO_NO_WEB` completes without the parser, xAI client or network WEB call. `MINI_FALLBACK` uses GPT-5 Mini. `SearchDecisionController` remains only the technical fallback when RuBERT or Mini cannot complete safely.

The policy prioritizes avoiding an unrequested external search. Discussion of the internet, search systems, current technology or a current entity is not automatically a WEB request. Direct requests to find, check or inspect current external information are WEB candidates; ambiguous contextual continuations may be delegated to Mini.

### Current web-search implementation

Real web/internet search is enabled by the centralized `SearchFeatureFlags.WEB_SEARCH_ENABLED` flag in `app/src/main/java/com/era/assistant/core/search/SearchFeatureFlags.kt`.

For a current search candidate, `SearchOrchestrator` checks the existing decision, optionally runs the separate `SearchIntentParser` with `gpt-5-mini`, and passes the normalized query (or the original query on parser failure) to `XaiSearchClient`. The client calls `https://api.x.ai/v1/responses` with the configured `web_search`/`x_search` tools. `EvidenceBundle` is then appended to the existing main OpenAI instruction path, where the selected Sphere model produces the user-facing answer.

For `NO_SEARCH`, `SearchOrchestrator` calls `onSuccess(null)` and the ordinary OpenAI conversation path continues without the search parser or xAI search client. The flag is the only current web-search gate; `XaiSearchClient` is reached through `SearchOrchestrator`, and the remaining xAI/OpenAI clients are independent subsystems.

The current implementation requires the existing xAI API-key URI for an actual search. `SearchOrchestrator` runs the cached local RuBERT WEB Router before any remote decision, off the UI thread, reusing one ONNX session per orchestrator. Mini load/API/malformed-response failures log `MINI_WEB_RUNTIME_FALLBACK` and use `SearchDecisionController`; RuBERT runtime failures use the same safe fallback. No failure crashes the application.

## Device Validation

Status: **DEVICE PARITY PASS**.

The Android debug self-test passed on a real device:

```text
TOKENIZER_LOAD: OK
ONNX_LOAD: OK
CLASSIFIER_LOAD: OK
TESTS_COMPLETED: 17/17
PARITY: PASS
MAX_ABS_DIFF: 0.000000558
```

Manual device validation confirmed explicit WEB search, no false WEB for ordinary conversation, no WEB from Qwen mention alone, Mini use of previous context, Mini clarification, and real search after Mini=`WEB`.

The RuBERT self-test is preserved as a debug diagnostic tool, not a production routing UI. It may later be extended for other local RuBERT tasks, including the memory subsystem.

## Phase Status

**CLOSED / COMPLETE FOR CURRENT PHASE.** Future extensions may expand WEB routing without changing this accepted state. Memory routing is the next major chapter and is not implemented here.

## Known Traps / Lessons

- Search evidence is optional context, not a replacement OpenAI conversation backend.
- Search evidence is grounding context, not user-facing answer text. The OpenAI prompt must explicitly require synthesis and must not encourage citation reproduction by default.
- A missing xAI key is an explicit error for a query that requires current evidence; ordinary queries can remain `NO_SEARCH`.
- Explicit search intent is matched as an action plus an internet/current-data target; standalone words such as `сегодня`, `сейчас`, `последний` or `актуальная` do not trigger search by themselves.
- Do not store API keys or authorization headers in raw evidence.
- Parser and tool-call variants must be verified against fixtures/source before changing routing.

## Required Verification

For search changes, inspect the decision, orchestrator, client, parser, archive and usage files relevant to the change; run targeted static/fixture checks and `git diff --check`. After the feature gate changes, a user/device build and network test are required for WEB, while a NO_WEB request must still reach the ordinary OpenAI path without the search parser or xAI client. Verify cancellation, citations, raw archive security and Usage UI behavior separately.
