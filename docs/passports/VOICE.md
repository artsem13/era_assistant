# Voice subsystem passport

## Current State

Voice Mode is a half-duplex session. The production capture path uses one `MediaRecorder` with the MIC/MPEG-4/M4A/AAC/mono/16 kHz/48 kbps profile. Recording starts before speech detection so a small pre-roll is retained. `MediaRecorderAmplitudeDetector` uses a device-relative background and sustained relative rise/return events. Audio grace ends one M4A; `VoiceTurnBuffer` merges useful batch STT results for one model turn.

The finalized M4A goes through batch `XaiSttClient`. The controller stops capture during model wait and TTS, then rearms a fresh recorder and detector before emitting `LISTEN_READY_ON`. TTS completion and manual interrupt use the existing rearm path. Failed rearm does not claim readiness or retry indefinitely.

Manual mic is separate: `MicInputUiController → VoiceInputController → VoiceRecorder → XaiSttClient`. TTS, GPT, memory and batch STT clients remain separate integrations. Built-in MIC is the production baseline; Voice Mode does not dynamically select Bluetooth input.

## State and boundaries

Voice Mode, `LISTEN_READY` and capture lifecycle are distinct signals. Production Voice does not use `AudioRecord` capture, streaming STT, transcript filtering or automatic acoustic barge-in. Black Box observability is a separate local diagnostic path.

## Known Traps / Lessons

- Do not stop and recreate the recorder at speech onset; the recorder must already be writing for pre-roll.
- Do not return to PCM/DSP/transcript filtering to compensate for an unverified capture route. Diagnose route → capture → signal → STT → endpointing → text processing.
- `getMaxAmplitude()` is device-relative; thresholds must use measured background, not a universal absolute level.
- TTS is intentionally deaf: no recorder or detector runs during model wait or playback.
- Bluetooth microphone routing and automatic barge-in remain postponed/disabled; they are not current capabilities.

## Portability

Voice code is portable in principle. `RECORD_AUDIO`, microphone route, Bluetooth behavior and OEM audio processing remain Android/device concerns. No export/import or persisted Voice schema migration is implemented.

## Required Verification

For Voice changes, inspect only the affected controller/capture/STT/TTS files and this passport. Static checks must confirm the intended capture path and unchanged manual mic boundary. Device verification is required for readiness events, pre-roll, natural pause, batch STT, rearm after TTS/interrupt and built-in MIC behavior.
