# Voice subsystem passport

## Current State

Voice Mode is a simple half-duplex session owned by `VoiceSessionController`. `MediaRecorderVoiceCapture` creates one `MediaRecorder` using the production MIC/MPEG-4/M4A/AAC/mono/16 kHz/48 kbps profile. The recorder starts before speech detection, so a small background pre-roll is retained. `MediaRecorderAmplitudeDetector` polls `getMaxAmplitude()` only while `LISTENING`, establishes a device-relative adaptive background, emits `USER_SPEECH_START` on a sustained relative rise, and emits `USER_SPEECH_END` after a sustained return near background. The audio grace (`TURN_GRACE_WINDOW_MS`) ends one M4A; the separate `VoiceTurnBuffer` grace is 1500 ms and merges successive useful batch STT results before one GPT user message.

The finalized M4A goes through the existing batch `XaiSttClient`. After TurnBuffer finalization, the controller stops capture, waits for the model, and keeps capture/detection off during `WAITING_MODEL` and `SPEAKING`. TTS playback completion or the manual interrupt button re-enters `STARTING_LISTENER`, prepares and starts a fresh recorder, starts the detector, and only then emits `LISTEN_READY_ON` and enters `LISTENING`. Voice Mode session ON/OFF and LISTEN_READY are separate signals. A failed rearm enters the existing error path without claiming readiness or retrying indefinitely.

Manual mic remains a separate `MicInputUiController` → `VoiceInputController` → `VoiceRecorder` → `XaiSttClient` path and was not changed. TTS transport, GPT pipeline, memory, and the batch STT client were not changed.

Bluetooth microphone routing is intentionally not selected or dynamically switched by Voice Mode. Built-in MIC is the production baseline; AirPods input routing is postponed to a separate task. Android may still route TTS output normally.

## Known Traps / Lessons

Do not stop and recreate the recorder at speech onset: the same recorder must already be writing. Do not return to AudioRecord, PCM sidecars, streaming STT, DSP, transcript filtering, or automatic acoustic barge-in. TTS is intentionally deaf: no recorder or detector exists during model wait or playback. `getMaxAmplitude()` is device-relative and must be interpreted against the measured background, not as an absolute universal level.

## Required Verification

Static: verify no production Voice reference remains to AudioRecord capture, `XaiStreamingSttClient`, the obsolete route controller, or automatic barge-in callbacks; verify the manual mic files and `XaiSttClient` are unchanged. Device: confirm recorder/detector readiness events, pre-roll and natural pause behavior, M4A batch STT, two-segment TurnBuffer merge, recorder OFF during model/TTS, playback-complete rearm, manual interrupt rearm, honest not-ready failure, and built-in MIC operation. Automatic barge-in must remain disabled.

Migration impact: portable Voice code; device-specific microphone permissions and capture behavior remain Android/device concerns. No export/import or persisted schema changes.
