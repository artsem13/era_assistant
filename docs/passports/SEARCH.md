# Search subsystem passport

## Current State

`SearchOrchestrator` uses `SearchDecisionController` to select `NO_SEARCH`, `GENERAL_WEB`, `SOCIAL_REALTIME_X` or `BOTH`. `XaiSearchClient` calls `https://api.x.ai/v1/responses` with model `grok-4.3`, low reasoning effort, sequential bounded tool calls and a 2 MiB response limit. Parsed evidence is appended to the existing OpenAI instruction path; OpenAI remains the final conversation response path.

`SearchRawArchive` writes request/response evidence to app-private `filesDir/xai_search_raw` only after secret scanning. API keys and authorization headers are not stored. `XaiSearchResponseParser` retains answer text, sources/citations, usage, latency and response identity. `SearchUsageTracker` persists xAI request/token/tool/latency/tick counters in `era_preferences` and `UsageActivity` displays provider-specific usage.

## Known Traps / Lessons

- Search evidence is optional context, not a replacement OpenAI conversation backend.
- A missing xAI key is an explicit error for a query that requires current evidence; ordinary queries can remain `NO_SEARCH`.
- Do not store API keys or authorization headers in raw evidence.
- Parser and tool-call variants must be verified against fixtures/source before changing routing.

## Required Verification

For search changes, inspect the decision, client, parser, archive and usage files relevant to the change; run targeted static/fixture checks and `git diff --check`. Human/device validation remains required for network, citation, cancellation and Usage UI behavior.
