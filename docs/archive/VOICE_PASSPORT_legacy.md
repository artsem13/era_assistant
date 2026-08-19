# ERA / SPHERE — VOICE PASSPORT

## 0. Status

This is the current snapshot of the production voice subsystem, audited from the
current code on 2026-08-16. Code and Git remain the final source of truth. This
document is a frozen baseline description, not a design proposal.

Classification used below:

- **CODE FACT** — directly confirmed in the current production code.
- **HUMAN VERIFIED** — explicitly confirmed by the latest available human/device evidence.
- **KNOWN LIMITATION** — an accepted current limitation or an unverified behavior.
- **POSTPONED** — deliberately outside this baseline.
- **HYPOTHESIS** — not asserted as a current fact unless separately marked.

## 1. Current Working Baseline

Voice Mode is the current **working / usable baseline**. Its production
architecture is simple half-duplex:

- one `MediaRecorder` writes an M4A before speech detection;
- `MediaRecorderAmplitudeDetector` measures recorder amplitude and identifies
  speech start/end;
- finalized M4A files go to batch xAI STT (`XaiSttClient`, language `ru`);
- `VoiceTurnBuffer` produces the user turn sent through the existing GPT path;
- xAI streaming TTS is played by `TtsPlaybackController` / `MediaPlayer`;
- the recorder and detector are OFF while waiting for the model and while TTS
  is playing;
- after playback, a fresh recorder is prepared and started before
  `LISTEN_READY` is reported.

Manual mic is a separate known-good baseline. It uses the same MediaRecorder
profile but does not share Voice Mode capture, detector, state machine, turn
buffer, or TTS.

Automatic acoustic barge-in is disabled. The current temporary way to interrupt
assistant speech is the explicit manual interrupt button. Bluetooth/AirPods
microphone routing is postponed. No complex DSP, VAD, AEC, filtering engine, or
PCM streaming engine is part of this baseline.

**FROZEN RULE:** do not rebuild the working Voice Mode without a separate task
and a new baseline/device test.

## 2. Architecture Overview

The production path is:

`Voice Mode button → VoiceModeController → VoiceSessionController →
MediaRecorderVoiceCapture → MediaRecorderAmplitudeDetector → M4A →
XaiSttClient → VoiceTurnBuffer → MainActivity/sendTextToSphere → existing
OpenAI Responses path → XaiStreamingTtsClient → TtsPlaybackController →
MediaPlayer → fresh recorder / LISTEN_READY`.

`VoiceModeController` is the small UI-facing wrapper. `VoiceSessionController`
owns session state, recorder lifecycle, grace timers, batch queue, STT, TTS
coordination, interrupt, and Black Box event emission. `MainActivity` supplies
the existing UI and GPT callbacks; it does not contain the voice engine.

The two capture paths are intentionally separate:

- manual mic: `MicInputUiController → VoiceInputController → VoiceRecorder →
  XaiSttClient`;
- Voice Mode: `VoiceModeController → VoiceSessionController →
  MediaRecorderVoiceCapture → detector → XaiSttClient`.

## 3. Manual Mic

### Current path

**CODE FACT.** The UI entry point is the manual mic `ImageButton`, bound by
`MicInputUiController`. A press starts recording; the next press stops it and
starts transcription. `VoiceInputController` owns the recording operation and
`VoiceRecorder` owns the `MediaRecorder`.

**CODE FACT.** Manual mic uses:

- `AudioSource.MIC` (`1`);
- MPEG-4 container;
- AAC encoder;
- mono;
- 16,000 Hz;
- 48,000 bit/s;
- `.m4a` output named `era_voice_<currentTimeMillis>.m4a` in `cacheDir`.

The file is sent as a batch multipart request through `XaiSttClient` to
`https://api.x.ai/v1/stt`, with `language=ru`. A useful transcript is placed
in the message input; empty/invalid results are not submitted. The temporary
file is deleted after success, error, or cancellation.

The manual path requires `RECORD_AUDIO` and a stored xAI API-key URI. It refuses
to start while Voice Mode is active. Voice Mode refuses to start while manual
recording is active.

**MANUAL MIC IS A REGRESSION-PROTECTED BASELINE.** Future Voice Mode work must
not change its behavior without a direct separate requirement.

## 4. Voice Mode

### End-to-end production flow

1. The Voice Mode button is bound by `VoiceModeController` and toggles the
   `VoiceSessionController` session.
2. Session start checks the xAI key, manual-mic exclusion, microphone
   permission, and audio focus.
3. A new `MediaRecorderVoiceCapture` is created. It prepares and starts a
   recorder before any speech decision.
4. Only after recorder start does the controller set `listenReady=true`, emit
   `LISTEN_READY_ON`, enter `LISTENING`, and start accepting detector events.
5. The detector emits activity start/end. End starts `AUDIO_GRACE`; a new start
   cancels it.
6. Audio-grace expiry calls `finishCurrentTurn(restart=true)`. The current M4A
   is finalized and a new recorder is started for the next listening period.
7. The finalized M4A is queued for sequential batch STT.
8. A useful transcript is appended to `VoiceTurnBuffer`; its finalization calls
   `submitTurn` and the existing `onVoiceMessage` callback.
9. The existing GPT/OpenAI Responses flow runs. Voice Mode only supplies a
   user message and receives model/TTS callbacks; it has no separate GPT
   architecture.
10. Model text is chunked by `TtsChunker`, processed by
    `TtsExpressionProcessor`, sent to `XaiStreamingTtsClient`, and queued as
    temporary MP3 files.
11. `TtsPlaybackController` plays the files with `MediaPlayer`.
12. On playback completion, the old capture is not reused: the controller turns
    readiness off, enters `STARTING_LISTENER`, creates a fresh recorder, and
    reports ready only after its actual start.

## 5. State Machine / Half-Duplex

### States

`VoiceModeState` contains `OFF`, `STARTING_LISTENER`, `LISTENING`,
`WAITING_MODEL`, `SPEAKING`, and `ERROR`.

| State | MediaRecorder | Detector | User speech accepted | Meaning |
|---|---|---|---|---|
| `OFF` | off | off | no | Voice session is stopped |
| `STARTING_LISTENER` | preparing/starting | off | no | recorder readiness is pending |
| `LISTENING` | on | on | yes | actual ready listening state |
| `WAITING_MODEL` | off | off | no | GPT/TTS input is being produced |
| `SPEAKING` | off | off | no automatically | TTS playback is active |
| `ERROR` | off | off | no | session has stopped on error |

### Production policy

This is intentionally half-duplex:

- **LISTENING:** MediaRecorder ON, detector ON, user speech accepted.
- **WAITING_MODEL:** MediaRecorder OFF, detector OFF, user speech not accepted.
- **SPEAKING/TTS:** MediaRecorder OFF, detector OFF, user speech not accepted
  automatically.
- **After TTS:** a recorder is created again; the detector starts; only actual
  recorder readiness produces `LISTEN_READY_ON` and `LISTENING`.

The purpose is to avoid self-listening to the app's own TTS and retain a simple,
stable working path. Recorder restart latency can exist at the boundary.

## 6. MediaRecorder Profile

The exact production Voice Mode profile is:

| Parameter | Voice Mode | Manual mic |
|---|---|---|
| source | `MIC` / `1` | `MIC` / `MediaRecorder.AudioSource.MIC` |
| container | MPEG-4 | MPEG-4 |
| codec | AAC | AAC |
| channels | 1 / mono | 1 / mono |
| sample rate | 16,000 Hz | 16,000 Hz |
| bitrate | 48,000 bit/s | 48,000 bit/s |
| file | `era_voice_turn_<sequence>.m4a` in `cacheDir` | `era_voice_<time>.m4a` in `cacheDir` |

The profiles are intentionally identical. Voice Mode starts writing before
speech detection; manual mic starts on the user button.

## 7. Background & Speech Detection

`MediaRecorderAmplitudeDetector` polls `MediaRecorder.getMaxAmplitude()` every
50 ms. It is an adaptive, device-relative level detector, not word recognition,
PCM RMS analysis, or a semantic STT component.

Current `VoiceModeConfig.AMPLITUDE_PROFILE`:

| Setting | Value |
|---|---:|
| polling interval | 50 ms |
| start ratio | 2.2 × background |
| end ratio | 1.35 × background |
| start consecutive samples | 1 |
| end hangover | 700 ms |
| background alpha | 0.05 |
| minimum background | 200 |
| minimum signal | 600 |
| summary interval | 1,000 ms |

The detector begins with background 200. When inactive, below-threshold samples
adapt the background using alpha 0.05. Start threshold is
`max(600, background × 2.2)`; end threshold is
`max(600, background × 1.35)`. One qualifying sample produces speech start.
While active, quiet audio must persist for 700 ms to produce speech end.

The detector only replaces the manual decision “I started speaking / I stopped
speaking.” It does not recognize words and must not be used as a transcript
repair mechanism.

## 8. Pre-Roll

**CODE FACT.** `MediaRecorderVoiceCapture.start()` starts the recorder before
`MediaRecorderAmplitudeDetector.start()`. The system does not use
`speech detected → start recorder`. Therefore recorder-start latency should not
remove the beginning of speech. Background audio before speech is retained in
the current M4A and is accepted as pre-roll.

## 9. Audio Grace

Audio grace is the acoustic-turn boundary. After detector end/hangover,
`VoiceSessionController` starts `AUDIO_GRACE_STARTED` for
`VoiceModeConfig.TURN_GRACE_WINDOW_MS = 1,500 ms`.

- a new speech-start event cancels it and keeps the same acoustic turn;
- expiry emits `AUDIO_GRACE_EXPIRED`, marks the turn finalizing, and finalizes
  the current M4A;
- the M4A is complete when the current recorder is stopped/released and the
  resulting non-empty file is available for batch STT;
- the recorder is then restarted for the next listening period.

Audio grace is about **audio file finalization**. It is not the TurnBuffer text
grace.

## 10. TurnBuffer Grace

`VoiceTurnBuffer` appends each useful, trimmed STT transcript and merges it with
the existing text. Its independent `TURN_BUFFER_GRACE_MS` is 1,500 ms. A new
segment resets that timer; expiry finalizes one text buffer and creates one USER
message. `finalizeNow()` also cancels the timer and clears the buffer.

The two mechanisms have different jobs:

- **AUDIO GRACE:** waits after acoustic silence, then stops one recorder/M4A.
- **TEXT/TURN BUFFER GRACE:** waits after a useful STT result, then joins text
  segments into one user turn.

The current controller appends useful batch results and then processes the batch
queue. The buffer remains the merge boundary and must not be confused with the
audio boundary.

Acceptance scenario: `"№1 = 357"` plus a pause and a following `"№2 = 865"`
can become one USER turn when the second STT segment arrives inside the merge
window. **HUMAN VERIFIED status for this exact scenario after the last repair is
not claimed here.**

## 11. LISTEN_READY and UI

The outer Voice Mode ring represents `VOICE_MODE_ON` / active Voice Mode. The
inner icon bars are the user-facing ready indication.

**CODE FACT.** Readiness is true only when the session is active, state is
`LISTENING`, `listenReady` is true, and TTS is not playing. `listenReady` is set
inside `onRecorderStarted`, after the actual `MediaRecorder.start()` call.

Bars/pulse are OFF when the session is off, connecting, waiting for the model,
speaking, in error, or during recorder rearm. They are ON only after actual
recorder readiness. During `WAITING_MODEL` and TTS the Voice Mode session may
remain on, but listening readiness is false. After playback, the UI turns
readiness off, starts recorder rearm, and turns it on only on the new start
callback.

The UI must reflect real Voice state. It must not imitate readiness with a
timer, guessed delay, or a static “ready” state.

## 12. Manual Interrupt

The interrupt button is visible and enabled only while the session is active,
state is `SPEAKING`, and TTS is actually playing. Its callback is
`VoiceSessionController.interruptAssistant("manual_button")`.

The controller logs the request, turns readiness off, enters
`STARTING_LISTENER`, clears/stops TTS and queued playback, waits for the
playback stop callback, calls the existing assistant-interrupt callback, and
starts a fresh recorder. The user can speak again only after that recorder has
started and `LISTEN_READY_ON` has been emitted.

Manual interrupt is the current production replacement for automatic barge-in.

## 13. Automatic Barge-In

Automatic acoustic barge-in is **DISABLED / NOT IN PRODUCTION**. The current
detector is not running during `SPEAKING`, so speech-based interruption cannot
be triggered automatically. The reusable stop/clear mechanism remains behind
the explicit manual interrupt path; these are not the same feature.

## 14. STT

Production STT is `XaiSttClient`:

- provider: xAI;
- endpoint: `https://api.x.ai/v1/stt`;
- request: synchronous batch multipart HTTP POST;
- fields: `language=ru` and `file`;
- input: non-empty M4A from MediaRecorder;
- response: JSON `text`;
- useful text: trimmed and containing at least one letter or digit;
- empty, punctuation-only, missing, or failed results: discarded; no USER
  message is submitted;
- Black Box events include batch start/result and transcript latency fields.

Streaming STT, PCM upload, smart-turn STT, and `XaiStreamingSttClient` are not
used by the production Voice Mode path. Any such legacy source is non-production
for this baseline.

## 15. GPT Integration

Voice Mode has no separate GPT architecture. The path is:

`voice transcript → USER message → existing onVoiceMessage /
MainActivity.sendTextToSphere(text, "voice") → existing OpenAI Responses path`.

Memory retrieval, conversation storage, and the normal response callbacks remain
owned by the existing application pipeline.

## 16. TTS

`XaiStreamingTtsClient` is the production xAI TTS client. It uses a WebSocket
stream with language `ru`, voice `eve`, codec `mp3`, and streaming-latency value
`1`. Model text is split by `TtsChunker`; expression processing occurs before
chunks are sent.

Audio deltas are accumulated until an audio segment is complete. Each segment is
written to a temporary `era_tts_*.mp3` in `cacheDir` and queued by
`TtsPlaybackController`. `MediaPlayer` prepares and plays one file at a time;
completed files are released and deleted.

Relevant lifecycle events are `TTS_CONNECT_START`, `TTS_READY`,
`TTS_FIRST_AUDIO_DELTA`, `TTS_AUDIO_DONE`, `PLAYBACK_QUEUE_ADD`,
`PLAYBACK_PREPARE_START`, `PLAYBACK_START`, `PLAYBACK_COMPLETE`, and TTS/error
clear/stop events. `PLAYBACK_COMPLETE` is emitted only when TTS input is marked
complete and the queue/player are empty.

At model request start, Voice Mode stops/discards capture and enters
`WAITING_MODEL`. During TTS, recorder and detector remain off. At playback
completion, the controller performs recorder rearm as described above. Stop,
error, and manual interrupt clear the TTS stream and temporary playback queue.

## 17. Black Box

### Capture and storage

Black Box is started by the existing Black Box UI through
`BlackBoxController.activate(context, BlackBoxProfile.VOICE_TTS, durationMs)`.
The `VOICE_TTS` profile writes JSON. Supported durations in the controller are
1, 5, 10, and 30 minutes; the session also ends on explicit user stop. On
Android 10+, files are saved through MediaStore at
`Download/Era/BlackBox/<timestamp>_voice_tts_<id>.json`. Older Android versions
use the app external Downloads `Era/BlackBox` directory.

Each session contains a format version, UUID, profile, start/end information,
requested duration, app/package and version metadata, Android SDK,
manufacturer/model, timezone, and ordered events with wall timestamp,
elapsed time, session ID, turn ID, generation, state, and event data.

### Current Voice/TTS event vocabulary

| Event | What it means |
|---|---|
| `VOICE_MODE_ON` / `VOICE_MODE_OFF` | session enabled / stopped |
| `VOICE_STATE_CHANGED` | VoiceModeState transition |
| `VOICE_ERROR` | session entered error |
| `MEDIA_RECORDER_PREPARED` | recorder prepare completed |
| `MEDIA_RECORDER_STARTED` | recorder actually started; readiness gate |
| `MEDIA_RECORDER_STOPPED` | current recorder stop boundary |
| `MEDIA_RECORDER_NEXT_START_GAP` | elapsed gap from prior stop to next start |
| `DETECTOR_STARTED` / `DETECTOR_STOPPED` | detector lifecycle |
| `BACKGROUND_BASELINE` | initial detector baseline reported |
| `AMPLITUDE_SUMMARY` | periodic amplitude/background summary |
| `USER_SPEECH_START` | detector accepted speech start |
| `USER_SPEECH_END` | detector accepted return to background |
| `AUDIO_GRACE_STARTED` / `AUDIO_GRACE_CANCELLED` / `AUDIO_GRACE_EXPIRED` | acoustic turn grace lifecycle |
| `M4A_FINALIZED` | non-empty M4A is ready for STT |
| `BATCH_STT_START` / `BATCH_STT_RESULT` | xAI batch request and outcome |
| `TURN_BUFFER_APPEND` | useful transcript added/merged |
| `TURN_BUFFER_GRACE_STARTED` / `TURN_BUFFER_GRACE_RESET` | text merge timer lifecycle |
| `TURN_BUFFER_FINALIZED` | one text buffer became a user turn |
| `TURN_SUBMIT` | voice text submitted to GPT pipeline |
| `CHAT_MESSAGE` | user/assistant message recorded in Black Box |
| `OPENAI_REQUEST_START` / `OPENAI_FIRST_DELTA` / `OPENAI_COMPLETED` / `OPENAI_ERROR` / `OPENAI_CANCEL` | existing GPT request lifecycle |
| `TTS_CONNECT_START` / `TTS_READY` / `TTS_RECONNECT` | TTS WebSocket lifecycle |
| `TTS_FIRST_AUDIO_DELTA` / `TTS_AUDIO_DONE` | TTS audio transport milestones |
| `PLAYBACK_QUEUE_ADD` / `PLAYBACK_PREPARE_START` / `PLAYBACK_START` | temporary audio queued/prepared/playing |
| `PLAYBACK_COMPLETE` / `PLAYBACK_ERROR` | queue fully completed / MediaPlayer failure |
| `TTS_CLEAR` / `AUDIO_CLEAR` / `TTS_STOPPED` / `TTS_SEND_FAILED` | TTS stop/clear/error path |
| `MANUAL_INTERRUPT_PRESSED` | interrupt button was pressed |
| `ASSISTANT_INTERRUPT_REQUESTED` / `ASSISTANT_INTERRUPT_COMPLETED` | manual interrupt lifecycle and latency |
| `LISTEN_READY_ON` / `LISTEN_READY_OFF` | actual recorder readiness entered/exited |
| `RETURN_LISTENING` | playback complete requested listener rearm |

The current production Voice Mode does not emit automatic acoustic barge-in
events. There is no detector or recorder event during `WAITING_MODEL`/`SPEAKING`.

### Diagnostic workflow

1. Open the app's Black Box control and activate the `VOICE_TTS` profile for a
   suitable duration.
2. Turn Voice Mode on.
3. Allow several seconds of background so `BACKGROUND_BASELINE` and amplitude
   summaries are captured.
4. Perform the problem scenario, including any manual interrupt if relevant.
5. Turn Voice Mode/Black Box off or wait for the selected capture duration to
   finish.
6. Find the JSON under `Download/Era/BlackBox/` on modern Android.
7. Inspect, in order: `VOICE_MODE_ON`, state transitions, recorder prepared/
   started events, `LISTEN_READY`, amplitude baseline/summaries, speech start/end,
   audio grace, M4A finalization, STT start/result, turn-buffer events, GPT
   events, TTS transport, playback start/complete, and interrupt events.

The Black Box is diagnostic logging, not audio recording; it does not contain
the raw M4A or TTS MP3 payloads.

## 18. Production File Map

| File | Responsibility | Used by |
|---|---|---|
| `app/src/main/java/com/era/assistant/MainActivity.kt` | minimal UI wiring, GPT callbacks, permissions, Black Box UI integration | both mic and Voice Mode |
| `core/voice/MicInputUiController.kt` | manual mic button, guards, permission/UI | manual mic |
| `core/voice/VoiceInputController.kt` | manual mic orchestration and STT callback | manual mic |
| `core/voice/VoiceRecorder.kt` | manual MediaRecorder and temporary M4A | manual mic |
| `core/voice/VoiceModeController.kt` | Voice Mode UI-facing wrapper | MainActivity |
| `core/voice/VoiceSessionController.kt` | Voice Mode state/lifecycle, turn/STT/GPT/TTS coordination | Voice Mode |
| `core/voice/MediaRecorderVoiceCapture.kt` | Voice Mode recorder, M4A lifecycle, detector bridge | session controller |
| `core/voice/MediaRecorderAmplitudeDetector.kt` | adaptive polling detector | Voice Mode capture |
| `core/voice/VoiceModeConfig.kt` | recorder/STT/TTS/detector constants | Voice Mode and TTS |
| `core/voice/VoiceModeState.kt` | production state enum | session/UI |
| `core/voice/VoiceTurnBuffer.kt` | transcript merge/finalization | session controller |
| `core/voice/XaiSttClient.kt` | batch xAI STT multipart client | manual mic and Voice Mode |
| `core/voice/XaiStreamingTtsClient.kt` | xAI streaming TTS WebSocket | Voice Mode |
| `core/voice/TtsChunker.kt` | model-text chunking | Voice Mode TTS |
| `core/voice/TtsExpressionProcessor.kt` | TTS text expression processing | Voice Mode TTS |
| `core/voice/TtsPlaybackController.kt` | temporary MP3 queue and MediaPlayer lifecycle | Voice Mode |
| `core/blackbox/BlackBoxController.kt` | timed JSON diagnostic sessions and event API | voice/TTS and app UI |
| `core/blackbox/BlackBoxProfile.kt` | `VOICE_TTS` profile | Black Box UI |
| `core/blackbox/BlackBoxStorage.kt` | MediaStore/external storage destination | Black Box |
| `core/blackbox/BlackBoxWriter.kt` / event/session classes | JSON format and ordered event persistence | Black Box |

Android `RECORD_AUDIO` permission is declared/requested through the existing
activity/controller wiring. Audio focus is owned by `VoiceAudioFocusController`.

## 19. Legacy / Non-Production

The following are not the current Voice Mode architecture when present in the
repository:

- `AudioRecord`/PCM Voice capture — **PRESENT BUT UNUSED / legacy**;
- PCM streaming STT and `XaiStreamingSttClient` — **PRESENT BUT UNUSED**;
- `VoiceAudioCapture` and `LocalSpeechEnergyDetector` — **PRESENT BUT UNUSED**;
- experimental AAC/MediaCodec or `VOICE_COMMUNICATION` capture candidates —
  **PRESENT BUT UNUSED**;
- automatic acoustic barge-in — **DISABLED, NOT PRODUCTION**;
- dynamic Bluetooth microphone routing — **POSTPONED / not a stable baseline**.

Do not revive a legacy file merely because it remains in the repository.

## 20. Bluetooth / AirPods

Bluetooth/AirPods microphone routing is **POSTPONED**. The stable baseline uses
the ordinary built-in `MIC` profile and does not claim guaranteed AirPods input.
System playback being audible through AirPods is not evidence that the
MediaRecorder microphone input is routed through AirPods.

No current Voice repair may add route switching as a side effect, change the
capture source, or treat output routing as input verification. Future Bluetooth
work is a separate project/pass with its own route, capture, and device test.

## 21. Known Good / Human Verified

### Code facts

- Manual mic and Voice Mode are mutually guarded and use batch M4A STT.
- Voice Mode starts recording before detector speech detection.
- Recorder and detector are off during model wait and TTS.
- Readiness is gated by the real recorder-start callback.
- Manual interrupt clears TTS and rearms a fresh listener.
- Automatic acoustic barge-in is not part of the current path.

### Human/device evidence

The available latest evidence identifies Voice Mode as a usable basic
conversation baseline and confirms the simplified MediaRecorder/batch-STT path.
Do not infer more device coverage than was explicitly tested. In particular,
this passport does not claim a fresh device verification of the two-segment
`№1 = 357` / `№2 = 865` merge scenario or guaranteed AirPods microphone input.

## 22. Known Limitations

- half-duplex only; no automatic speech interruption while TTS plays;
- manual interrupt is required to stop assistant speech early;
- AirPods/Bluetooth microphone input is not guaranteed;
- no advanced DSP, AEC, echo cancellation, or semantic VAD;
- recorder rearm can introduce a measurable gap;
- audio and text grace boundaries can split or merge turns depending on timing;
- exact TurnBuffer merge scenario is not freshly device-verified here;
- Black Box records events/metadata, not raw audio or TTS bytes;
- stale documents may describe removed behavior and must not override code.

## 23. Frozen Baseline Rules

# FROZEN BASELINE — DO NOT BREAK

1. Do not change manual mic without a separate task.
2. Do not change the Voice MediaRecorder profile without an A/B human test.
3. Do not change `XaiSttClient` as part of unrelated Voice work.
4. Half-duplex is the current production policy.
5. Do not accidentally return automatic barge-in.
6. Do not add AirPods routing as a side effect.
7. Do not return to AudioRecord/PCM/streaming STT without a separate decision.
8. `LISTEN_READY` must reflect actual recorder readiness.
9. Audio grace and TurnBuffer grace are different mechanisms.
10. Any future Voice repair starts by reading `VOICE_PASSPORT.md`.

## 24. Future Work / Postponed

Only these directions are postponed; they are not part of this design:

- automatic barge-in;
- echo/self-voice filtering;
- advanced detector/VAD;
- a possible dedicated audio engine;
- AirPods/Bluetooth microphone routing;
- improved interrupt UX;
- custom voices and voice settings;
- TTS expression/emotion audit.

## 25. Future Codex Entry Point

For any future Voice/Audio/STT/TTS/Black Box voice task:

1. Read `AGENTS.md` completely.
2. Read `VOICE_PASSPORT.md` completely.
3. Read `docs/agent-context/VOICE.md` if it is still used.
4. Determine the affected subsystem.
5. Only then read the relevant code files.

This passport is a snapshot of the current human-tested production baseline. If
code and passport differ, do not automatically change code to match the
passport. First report the discrepancy. Code remains the final source of truth.

## 26. Last Audit / Verification

- Audit date: 2026-08-16.
- Inspected production voice/Black Box files: **18 named files/classes**, plus
  the minimal `MainActivity` wiring and related existing GPT callbacks.
- Production code changed: **NO**.
- Build: **NOT RUN** (documentation-only pass, as required).
- Commit/push: **NOT PERFORMED**.
- `git diff --check`: required final check; result to be reported with the final
  Git check.
- Human verification: current usable Voice Mode baseline and the simplified
  MediaRecorder/batch-STT behavior are recorded only to the extent supported by
  the available latest evidence; unverified scenarios are explicitly marked.
- This passport was created from the current code after the final Voice Mode
  simplification.

`VOICE_AUDIT_CURRENT.md` is a historical snapshot created before the final
simplification. It must not be treated as the current production passport.
