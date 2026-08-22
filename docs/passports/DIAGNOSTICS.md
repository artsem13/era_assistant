# Diagnostics Passport

## Current state

Era records a local-only chronological diagnostic stream through `EraDiagnosticsLogger`. Events are appended asynchronously to the app-private SQLite database `era_diagnostics.db` (`diagnostic_events` table) under `Context.getDatabasePath("era_diagnostics.db")`, normally the app's private `databases/` directory. The logger has no HTTP, analytics, cloud or model dependency and suppresses its own failures.

Each event stores epoch milliseconds, device-local ISO datetime, current `TimeZone.getDefault().id`, UTC offset, optional conversation/turn/message identifiers and a JSON payload. Current integrations record `USER_MESSAGE`, `ASSISTANT_MESSAGE`, `RUBERT_WEB_DECISION`, `MINI_REQUEST`, `MINI_RESULT`, `WEB_SEARCH_REQUEST`, `WEB_SEARCH_RESULT`, `ROUTE_STATE_CHANGE` and `NOTE_CREATED`. Mini and WEB events include duration fields where completion/failure is observed. `NOTE_UPDATED` and `NOTE_DELETED` are not generated because the current Notes implementation has no update/delete API.

The summary API returns user/assistant totals, RuBERT decisions, Mini request/result breakdown, WEB request/result success/failure counts, note create/update/delete counts and average Mini/WEB durations. It is a read-only query surface; there is no diagnostics UI or automatic export trigger in the current code.

`EraDiagnosticsLogger.exportToDownload()` makes a local copy at `/storage/emulated/0/Download/Era/diagnostics/era_diagnostics_YYYY-MM-DD_HH-mm-ss.db`. This is a backend function; public-storage permission/policy and device behavior still require verification. No automatic retention or cleanup is enabled.

## Known limits and required verification

Notes currently have no separate list screen; new archive note rows receive created/updated epoch/local datetime and timezone fields, while old rows remain nullable. Turn correlation is not fully guaranteed: MainActivity owns the user/assistant turn id, but the RuBERT event currently has no conversation/turn id and WEB source-route labeling is currently fixed to `AUTO_WEB` in `XaiSearchClient`. Device verification must confirm one real turn and one Note produce consistent local timestamps. Export requires the device storage policy/permission to permit the requested public Download path.
