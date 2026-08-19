# Usage subsystem passport

## Current State

OpenAI usage and balance counters are stored in the shared `era_preferences` preferences and displayed by `UsageActivity`. The current UI has separate OpenAI and xAI pages/tabs. xAI counters are recorded by `SearchUsageTracker`, including requests, input/cached/output/reasoning/total tokens, web/X/tool counts, last latency and cost ticks. OpenAI model counters and balance display remain in the existing MainActivity/UsageActivity path.

## Known Traps / Lessons

- Usage counters are local estimates/state, not authoritative provider billing.
- xAI cost ticks and token fields may be nullable in provider responses; missing values are handled as zero by the tracker.
- Usage UI changes must not be confused with changes to the conversation or search transport.

## Required Verification

For Usage changes, inspect `UsageActivity`, `UsageProviderController`, `SearchUsageTracker` and the relevant preference keys; use targeted read-back and `git diff --check`. Device/UI verification is required for tabs, counters and formatting.
