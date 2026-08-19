# xAI Internet Search v0.1 — Production Implementation Report

Date: 2026-08-17 UTC

## Result

The xAI Internet Search v0.1 production integration is present in the current
working tree. The final implementation was not reverted or rewritten during
this continuation.

The canonical build was started from the current state with:

```sh
bash tools/build-debug.sh
```

It reached `:app:mergeDebugResources`, then the Gradle 6.1.1 daemon
disappeared. The only reported failure was:
`Gradle build daemon disappeared unexpectedly`; no Kotlin, resource, AAPT2,
or dependency error was reported. This is the documented sandbox/environment
failure mode. The previous canonical build had already reported `BUILD
SUCCESSFUL` before the final Usage UI/counter and parser-warning edits. A new
final APK cannot therefore be claimed from this continuation; the existing
APK predates those last edits.

## Implemented production path

- `SearchDecisionController` routes queries to `GENERAL_WEB`,
  `SOCIAL_REALTIME_X`, `BOTH`, or `NO_SEARCH`.
- `XaiSearchClient` uses `POST https://api.x.ai/v1/responses`, model
  `grok-4.3`, `reasoning.effort=low`, sequential tool calls, bounded output,
  connect/read timeouts, cancellation, and a 2 MiB response limit.
- Tool selection is `web_search`, `x_search`, or both. Web sources and X
  `custom_tool_call`/`x_search_call` variants are retained by the parser.
- `XaiSearchResponseParser` preserves answer text, actions, encountered
  sources, citation URLs/titles/indices, nullable usage fields, latency,
  response id, and authoritative `cost_in_usd_ticks`.
- Evidence is appended to the existing OpenAI instruction path as external
  evidence with an explicit URL boundary; the existing OpenAI streaming path
  remains the final response path.
- `SearchRawArchive` stores request/response evidence under app-private
  `filesDir/xai_search_raw` only after API-key secret scanning. API keys and
  authorization headers are not stored.
- `SearchUsageTracker` accumulates request, token, tool-call, latency, and
  tick-based cost counters. Usage UI has OpenAI/xAI tabs, swipe navigation,
  xAI session counters, tool counters, and cost display.
- Main UI has a production search status card, animation lifecycle handling,
  cancellation, and debug-only preview entry.

## Offline verification

- `git diff --check`: PASS.
- Four empirical fixtures in `xai_probe/raw/`: PASS against the observed
  output/message/usage contract, including HTTP 200, output messages,
  `total_tokens`, and `cost_in_usd_ticks`.
- Modified XML resources: PASS with XML parsing.
- Static routing/parser/usage/security invariants: PASS.
- Secret-like token scan over implementation and raw fixtures: PASS.
- AAPT2 override remains configured in `gradle.properties`:
  `/data/data/com.termux/files/usr/bin/aapt2`.

## Scope and files

The current Git status includes the prior task's tracked edits to `AGENTS.md`,
`MainActivity.kt`, `UsageActivity.kt`, `activity_main.xml`, and
`activity_usage.xml`, plus the new search/UI source, drawable, raw video,
`xai_probe/`, and `video/` artifacts. This report is an additional untracked
file. No commit, push, reset, checkout, restore, or clean was performed.

`MainActivity.kt` changed by `+103/-1` lines in the current diff; its added
logic is wiring/lifecycle/callback integration, while search behavior remains
in dedicated classes.

No subsystem passport was updated: no INTERNET or USAGE passport exists under
`docs/agent-context/`, and Voice was outside this task.

## Human test and migration

Human/device testing remains required: verify a current-information query,
X-specific query, combined query, no-search query, missing-key error,
cancellation, citation display/context, raw archive contents, and Usage tab
counters on the target Android device.

Migration impact: portable app-private search archive and SharedPreferences
usage state; no device-specific schema migration was introduced. API-key URI
permission and Android device/network availability remain device/runtime
requirements.

Known limitation: the current continuation could not produce a final APK
because the build daemon was killed by the environment after resource merge;
run `bash tools/build-debug.sh` in the canonical Termux/proot Debian build
environment to rebuild the exact final state.
