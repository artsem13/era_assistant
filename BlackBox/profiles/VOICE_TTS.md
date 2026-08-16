# VOICE_TTS profile

## Purpose

VOICE_TTS records the technical chain behind Voice Mode symptoms: speech capture, xAI streaming STT, turn submission, memory retrieval, OpenAI response, xAI TTS WebSocket, audio delivery, playback and return to listening. It does not change Voice behavior and does not touch the separate manual microphone path.

## Events

The v0.1 timeline covers `VOICE_MODE_ENABLED`, `VOICE_MODE_DISABLED`, `VOICE_STATE_CHANGED`, `STT_CONNECT_START`, `STT_READY`, `AUDIO_CAPTURE_START`, `AUDIO_ROUTE_REQUESTED`, `AUDIO_ROUTE_CHANGED`, `AUDIO_INPUT_CONFIRMED`, `AUDIO_ROUTE_FALLBACK`, `LOCAL_SPEECH_START`, `LOCAL_SPEECH_END`, `FIRST_PCM_CAPTURED`, `FIRST_PCM_SENT`, `SPEECH_DETECTED`, `FIRST_PARTIAL`, `STT_PARTIAL_TEXT`, `STT_FINAL_TEXT`, `SPEECH_FINAL`, `TURN_SUBMIT`, `CHAT_MESSAGE`, `MEMORY_RETRIEVAL_START`, `MEMORY_RETRIEVAL_END`, `OPENAI_REQUEST_START`, `OPENAI_FIRST_DELTA`, `OPENAI_COMPLETED`, `OPENAI_ERROR`, `OPENAI_CANCEL`, `TTS_CONNECT_START`, `TTS_READY`, `TTS_SEND_FIRST_TEXT`, `TTS_SEND`, `TTS_SEND_FAILED`, `TTS_FIRST_AUDIO_DELTA`, `TTS_AUDIO_DONE`, `PLAYBACK_QUEUE_ADD`, `PLAYBACK_PREPARE_START`, `PLAYBACK_START`, `PLAYBACK_COMPLETE`, `PLAYBACK_ERROR`, `BARGE_IN_DETECTED`, `BARGE_IN_CONFIRMED`, `TTS_CLEAR`, `AUDIO_CLEAR`, `STT_RECONNECT`, `TTS_RECONNECT`, `TTS_SOCKET_CLOSE`, `RETURN_LISTENING`, and `VOICE_ERROR`.

`LOCAL_SPEECH_START` and `LOCAL_SPEECH_END` are diagnostic-only transitions from adaptive RMS analysis of the exact PCM chunks already sent to xAI STT. They do not finish a turn, send text, control Smart Turn, change endpointing, or affect TTS/playback. The initial detector constants are: start ratio 2.2x noiseFloor, end ratio 1.4x, 250 ms start stability, 600 ms end hangover, and noise-floor alpha 0.05. These are relative PCM values, not dB(A).

Each event carries sessionId; Voice events also carry turnId/generation when known. Data may include phase, service, operation, character count, socket lifecycle state, ready flag, close code/reason, reconnect attempt, outstanding utterances, playback queue size and sanitized error class/message. Text events carry the complete text while the VOICE_TTS Black Box session is ACTIVE: `STT_PARTIAL_TEXT` and `STT_FINAL_TEXT` use the exact xAI transcript string, while `CHAT_MESSAGE` uses `role=user` for the exact user string sent to the chat/OpenAI pipeline and `role=assistant` for the final completed model response. Deltas are not emitted as CHAT_MESSAGE events. No API key, Authorization header, raw audio or credentials are stored; recognizable credential-like fragments inside text remain redacted.

## Reading a failure

Sort events by `elapsedMs`. Compare `LOCAL_SPEECH_START`/`LOCAL_SPEECH_END` with xAI `SPEECH_DETECTED`/`SPEECH_FINAL` on this same monotonic timeline. For example, a local end after `SPEECH_FINAL` supports the hypothesis that xAI endpointing completed early; a local end before `SPEECH_FINAL` provides less support. Route intervals are reconstructed from the latest confirmed input event until the next confirmed route change; requested events alone must not be used as proof of PCM origin.

The last successful stage is the latest expected event before a gap; the first failed stage is the first `*_FAILED`, `*_ERROR`, close, or `VOICE_ERROR` event. Correlate by `turnId` and `generation`, especially after barge-in. Useful latency intervals are:

* `SPEECH_FINAL` → `TURN_SUBMIT`;
* `TURN_SUBMIT` → `MEMORY_RETRIEVAL_END`;
* `OPENAI_REQUEST_START` → `OPENAI_FIRST_DELTA`;
* first delta/`OPENAI_COMPLETED` → first TTS send;
* TTS send → `TTS_FIRST_AUDIO_DELTA`;
* first audio → `PLAYBACK_START`;
* playback start → `PLAYBACK_COMPLETE`.

If text reaches OpenAI but there is no TTS send, inspect the Voice/TTS transition and generation. If send exists but no audio delta, inspect socket state and close/error events. If audio exists but no queue/playback events, inspect the handoff to MediaPlayer. This profile observes the current implementation; it does not infer or fix the root cause.
