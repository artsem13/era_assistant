# Black Box JSON format v1

## Session

Файл UTF-8 и после штатного stop является одним JSON object:

```json
{
  "formatVersion": 1,
  "sessionId": "uuid",
  "profile": "VOICE_TTS",
  "startedAt": "2026-08-15T10:22:14.183+00:00",
  "durationRequestedMs": 300000,
  "metadata": {},
  "events": [],
  "endedAt": "2026-08-15T10:27:14.210+00:00",
  "endReason": "USER_STOPPED"
}
```

Required session fields are `formatVersion`, `sessionId`, `profile`, `startedAt`, `durationRequestedMs`, `endedAt`, `endReason`, `metadata`, and `events`. `formatVersion` is an integer. Profile and end reason use stable uppercase wire names.

## Event

Every event has `event`, `timestamp`, `elapsedMs`, `sessionId`, and `data`. Optional correlation fields are `turnId`, `generation`, and `state`.

`timestamp` is a wall-clock ISO-8601 timestamp with milliseconds and offset. `elapsedMs` is the duration from the session's monotonic start, measured with `SystemClock.elapsedRealtime()`. It is not calculated from wall-clock timestamps and can be compared safely across events in one session. `data` contains sanitized, event-specific values. For the VOICE_TTS text events below, `data.text` preserves the complete transcript or message text (including ordinary wording and length beyond the technical sanitizer limit); only explicitly recognizable credential-like fragments are redacted. These text events are emitted only while an ACTIVE VOICE_TTS session exists.

## Voice input route diagnostics

For Voice Mode, `AUDIO_ROUTE_REQUESTED` records the preferred route selected by the app. It is not proof of the physical PCM source. `AUDIO_INPUT_CONFIRMED` and `AUDIO_ROUTE_CHANGED` describe the route observed from the active `AudioRecord`; on Android versions where the platform cannot expose that observation, the event keeps `confirmed=false` and `inputType=UNKNOWN`. Technical route data may contain `inputType`, numeric `deviceType`, safe `deviceName`, `isBluetooth`, `isBuiltIn`, `selectionSucceeded`, and `reason`. MAC addresses, Bluetooth identifiers, and credentials are never written.

`elapsedMs` remains the only ordering key needed for route changes and speech events.

### Voice text events

`STT_PARTIAL_TEXT` records each significant partial transcript actually received from xAI STT. `STT_FINAL_TEXT` records each final transcript string actually received from xAI STT. Both include `text`, `textLength`, optional `confidence`, and the standard `turnId`, `generation`, and `state` correlation fields. No artificial partial events are generated.

`CHAT_MESSAGE` records a completed message at the existing chat pipeline boundary. It uses `role` (`user` or `assistant`), the complete `text`, and `textLength`, with the applicable turn and generation correlation. User text is the exact string passed to the OpenAI pipeline; assistant text is the final completed response, not individual deltas.

Errors should use safe fields such as `operation`, `service`, `exceptionClass`, sanitized `message`, `httpStatus`, `socketState`, `closeCode`, and `closeReason`. Never put credentials into data or exception text.

## Extension and compatibility

New optional event fields and data keys are backward-compatible. Existing fields must keep their meaning. A new required field or incompatible type requires a new `formatVersion`. Unknown events and data keys must be ignored by readers. A process crash can leave a partial stream without the closing JSON footer; the last flushed prefix remains useful for diagnosis, while a normally stopped session is valid JSON.
