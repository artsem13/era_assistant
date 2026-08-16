# Voice Mode Current Audit

Дата аудита: 2026-08-16. Это read-only аудит текущего dirty tree. Код и Git являются источником истины; существующий dirty tree зафиксирован как baseline до создания этого файла. Runtime-файл `2026-08-16_124254_voice_tts_7de9f4a7.json` в репозитории не найден, поэтому раздел runtime evidence ограничен предоставленными пользователем фактами и кодом.

Классификация в этом документе:

- **FACT** — непосредственно следует из текущего кода или доступного runtime evidence.
- **FINDING** — объективное несоответствие/проблема, видимая в текущем состоянии.
- **RISK** — кодовый путь, который может привести к проблеме, но не доказывает её на конкретном устройстве.
- **HYPOTHESIS** — возможная причина пользовательского симптома, не доказанная этим аудитом.

## 1. Executive Summary

**FACT.** Production Voice Mode — это `VoiceSessionController` → `MediaRecorderVoiceCapture` → один активный `MediaRecorder` на acoustic turn → `MediaRecorderAmplitudeDetector` через `getMaxAmplitude()` → M4A batch upload в `XaiSttClient` (`https://api.x.ai/v1/stt`, language `ru`) → `VoiceTurnBuffer` → `MainActivity.sendTextToSphere()` → OpenAI Responses streaming → `XaiStreamingTtsClient` → `TtsPlaybackController`/`MediaPlayer`. Recorder не останавливается на начале речи, при запросе GPT или во время TTS; он останавливается только на истечении turn grace, при route-recreate или остановке сессии.

**FACT.** Detector запускается после первого `MediaRecorder.STARTED` и продолжает polling в `LISTENING`, `WAITING_MODEL` и `SPEAKING`. При activity во время `SPEAKING` вызывается barge-in без transcript/STT.

**FINDING.** Визуальный white-bars icon — статический `ic_voice_mode`, он виден всегда. Красный ring/pulse запускается уже при включении Voice Mode, до `MediaRecorder.STARTED`, и не является подтверждением recorder readiness, route confirmation или STT readiness. Таким образом, UI может показывать активную ready-like индикацию в `CONNECTING`.

**FINDING.** Текущая реализация route — `MediaRecorderRouteController`, а не описанный в старых Black Box docs `AudioRecord` route controller. Он наблюдает `MediaRecorder.routedDevice`, но при отсутствии наблюдаемого устройства не пишет отрицательное подтверждение и игнорирует Boolean результат `setCommunicationDevice()`.

**FACT.** Доступный runtime JSON не найден. Предоставленные runtime facts принимаются как external evidence: `VOICE_ACTIVITY_START` возникал; detector работал; built-in route подтверждался; recorder restart gaps были порядка сотен миллисекунд; activity возникала в `SPEAKING`; `BARGE_IN_DETECTED` присутствовал.

**FINDING.** Для AirPods фактический входной PCM route не доказывается самим Bluetooth playback/подключением. В текущем коде есть потенциальные failure points в выборе input device, SCO/communication-device lifecycle, ignored route result и deferred/recreated recorder. Конкретная точка пользовательской неисправности этим code-only audit не доказана.

## 2. Current Production Pipeline

1. `MainActivity` создаёт `MicInputUiController` и `VoiceModeController`; Voice Mode button передаётся в `VoiceModeController.bind()`.
2. Нажатие кнопки вызывает `VoiceSessionController.startSession()` через click listener. Проверяются xAI key URI и отсутствие manual mic recording. Если нет `RECORD_AUDIO`, состояние становится `CONNECTING`, запрашивается permission.
3. После permission (или сразу при уже выданном permission) запрашивается audio focus, ставится `sessionActive=true`, запускается `PulseRingAnimator`, пишется `VOICE_MODE_ON`, затем `startCapture()`.
4. `startCapture()` ставит состояние `CONNECTING`, создаёт `MediaRecorderVoiceCapture`, который вызывает `MediaRecorderRouteController.begin()`, устанавливает communication mode, регистрирует `AudioDeviceCallback` и выбирает лучший input.
5. Capture создаёт файл `cacheDir/era_voice_turn_<sequence>.m4a`, задаёт `MIC` (source value 1), MPEG-4, AAC, mono, 16 kHz, 48 kbps, вызывает `prepare()`, затем `start()`. Только после этого `captureReady=true`, логируется `MEDIA_RECORDER_STARTED`, состояние становится `LISTENING`.
6. После recorder start запускается `MediaRecorderAmplitudeDetector`. Каждые 50 ms он читает `recorder?.getMaxAmplitude()`. Один qualifying sample выдаёт `VOICE_ACTIVITY_START`; detector не останавливает recorder.
7. При activity `VoiceSessionController` помечает acoustic turn, отменяет grace и, если state `SPEAKING`, запускает barge-in. При возврате в тишину detector после 700 ms hangover выдаёт `BACKGROUND_RETURN`, после чего начинается turn grace 1500 ms.
8. Если новый activity не отменил grace, grace expiry делает `finalizingTurnId`, вызывает `capture.finishCurrentTurn()`: текущий recorder stop/reset/release, M4A finalization, удаление/постановка batch и немедленный запуск следующего recorder + detector.
9. M4A отправляется отдельным thread через multipart POST `XaiSttClient` на `https://api.x.ai/v1/stt` с полями `language=ru` и `file`. Это batch STT; streaming STT в этом pipeline не вызывается.
10. Непустой alphanumeric STT result добавляется в `VoiceTurnBuffer`, после чего текущий код сразу вызывает `finalizeNow()`. Поэтому фактическая batch boundary/grace — recorder turn; `VoiceTurnBuffer` grace 1500 ms существует, но при обычном успешном batch результате не используется как дополнительное ожидание.
11. `submitTurn()` меняет state на `WAITING_MODEL` и вызывает `onVoiceMessage`, то есть `MainActivity.sendTextToSphere(text, "voice")`. MainActivity сохраняет user message, memory retrieval, затем отправляет OpenAI Responses request.
12. `onNewRequest()` очищает текущую речь, увеличивает turn generation, ставит `WAITING_MODEL`, запускает TTS transport. OpenAI deltas идут в `TtsChunker`; завершённые chunks проходят expression processor и передаются в xAI streaming TTS.
13. TTS audio bytes складываются в temporary MP3 files и очередь `TtsPlaybackController`. Первый `MediaPlayer` playback переводит state `WAITING_MODEL → SPEAKING`. После TTS input complete, queue empty и player released логируется `PLAYBACK_COMPLETE`, state возвращается в `LISTENING` (если capture ready) или `CONNECTING`.
14. На stop Voice Mode или error session прекращается, detector/route/recorder/TTS/audio focus останавливаются, pending M4A удаляются, state становится `OFF` или `ERROR`.

## 3. State Machine

Общие факты таблицы: `MediaRecorder active` означает `capture.isRunning()`; detector активен только после recorder start; ring active означает `PulseRingAnimator.start()` до stop/error; white bars — static icon, не state indicator.

| State | Что запущено | Recorder / polling | Detector events / STT | TTS / speech | UI и transitions |
|---|---|---|---|---|---|
| `OFF` | Ничего Voice session | нет / нет | events не принимаются; STT нет | TTS stop/released; user speech не разрешена Voice Mode | ring отсутствует; white bars static. Click → `CONNECTING` (или key/manual-mic guard) |
| `CONNECTING` | permission/audio focus, route setup или recorder recreate | обычно recorder ещё не active; polling нет; при route recreate старый recorder уже остановлен | detector не принимает; STT не стартует до grace/recorder readiness | TTS обычно не играет | **ring уже может быть active**; bars static. `MEDIA_RECORDER_STARTED` → `LISTENING`; capture error → `ERROR`; permission denial → `ERROR` |
| `LISTENING` | continuous capture, route callbacks, detector, pending batches | active / active | принимает activity/end; grace expiry может start batch STT | normally no TTS; user speech разрешена | ring active; icon bars static. activity→turn/grace; grace expiry→новый recorder, STT async; user transcript→`WAITING_MODEL`; stop→`OFF` |
| `WAITING_MODEL` | OpenAI/memory/TTS transport; capture не остановлен | active / **active** | принимает activity; grace expiry может start STT даже в этом state | TTS input/queue may be filling; playback may not have started | ring active; bars static. first playback→`SPEAKING`; completion/error→listening; activity itself does not force barge-in here |
| `SPEAKING` | TTS playback и continuous capture/detector | active / **active** | принимает activity; `VOICE_ACTIVITY_START` вызывает barge-in; grace expiry/STT также possible | MediaPlayer active; user speech may barge-in | ring active; bars static. activity→barge-in→stop/clear→`LISTENING`; queue complete→`LISTENING`; stop→`OFF` |
| `ERROR` | error UI only; sessionActive=false | no / no | no | stop/clear | ring stopped; bars static. No automatic recovery transition; new click can start a new session because `released` is false |

**FACT.** Detector действительно продолжает работать в `WAITING_MODEL` и `SPEAKING`: его lifecycle привязан к capture, а `onNewRequest()` и `playbackStarted()` recorder не останавливают.

**RISK.** Пока модель отвечает или TTS говорит, grace expiry может завершить акустический segment и запустить batch STT; state itself не блокирует `finishCurrentTurn()`/`processNextBatch()`.

## 4. UI / Listening Ready Indicator

**White bars.** `activity_main.xml` всегда использует `@drawable/ic_voice_mode`; vector содержит пять белых vertical bars. Ни `VoiceSessionController.state`, ни `captureReady`, ни route, ни STT/TTS callbacks не меняют этот icon. Это static icon, а не ready signal.

**White/red ring.** `PulseRingAnimator.start()` вызывается в `startSession()` сразу после audio focus и до `startCapture()`. При permission path он вызывается в `onRequestPermissionsResult()` также до `startCapture()`. Ring ставится как button foreground и анимируется независимо от route и recorder. `setIconPulseEnabled()` меняет только alpha icon; он включён лишь при `sessionActive && state == LISTENING && captureReady`.

**FACT.** `captureReady` становится true только внутри `onRecorderStarted()`, после `MediaRecorder.start()`. Но ring уже запущен в `CONNECTING`. `PulseRingAnimator` сам не знает route confirmation и STT readiness.

**FINDING.** Ready-like visual indication появляется раньше фактического recorder readiness. Static white bars всегда видны, а ring активен во время connecting. Route confirmation не является условием UI. MediaRecorder STARTED не является условием запуска ring. STT readiness вообще не существует как gate. TTS state не выключает ring; во время `WAITING_MODEL`/`SPEAKING` он продолжает быть видимым.

## 5. MediaRecorder Lifecycle

- При session start создаётся `MediaRecorderVoiceCapture`, затем первый recorder.
- `prepare()` precedes `onRecorderPrepared`; `start()` precedes assignment to `recorder`, `routeController.attach()`, `recordingStarted()`, `onRecorderStarted`.
- Файлы: `cacheDir/era_voice_turn_1.m4a`, then incrementing sequence. TTS files are separate `era_tts_*.mp3`; manual mic uses `era_voice_<epoch>.m4a`.
- `finishCurrentTurn()` logs stop, stops detector, calls `stopRecorder()`, then emits M4A if non-empty, then immediately creates the next recorder and restarts detector.
- `stopRecorder()` detaches route listener, calls `stop/reset/release`; invalid/empty files are deleted. `finishCurrentTurn()` valid M4A remains until STT callback; `finishBatch()` deletes it. Stopped sessions delete queued/in-flight files.
- `applyRouteAtSafeBoundary()` deletes the current recorder file without STT, starts a new recorder and restarts detector. This is route-recreate, not a completed user turn.
- On route switch at an active speech boundary, `finishCurrentTurn()` both finalizes the current M4A and creates the next recorder; the requested route is applied to the new recorder.

**FACT.** Recorder restart gaps are instrumented by `MEDIA_RECORDER_NEXT_START_GAP` (`started timestamp - last stop timestamp`). The supplied runtime observation reports gaps of hundreds of ms. Exact JSON is unavailable here, so no exact sample distribution is claimed.

**RISK.** There are windows between stop/release and next `MEDIA_RECORDER_STARTED` where no recorder and no detector exist. The UI ring does not necessarily disclose this gap.

## 6. Amplitude Detector

Production profile (`VoiceModeConfig.AMPLITUDE_PROFILE`): polling `50 ms`; `startRatio=2.2`; `endRatio=1.35`; `startConsecutiveSamples=1`; `endHangoverMs=700`; `backgroundAlpha=0.05`; minimum background `200`; minimum signal `600`; summary interval `1000 ms`.

Algorithm: `getMaxAmplitude()` is read through the current recorder, exceptions become 0, and negative values are clamped. Start threshold is `max(600, background * 2.2)`. End threshold is `max(600, background * 1.35)`. While inactive, above-start samples count; below threshold resets the count and updates background with `background += .05 * (max(200, amplitude)-background)`. One above-threshold sample emits `VOICE_ACTIVITY_START`. While active, samples at/below end threshold begin/continue quiet timer; after 700 ms `VOICE_ACTIVITY_END` is exposed to capture as `BACKGROUND_RETURN`. Loud samples clear quiet timer. `stop()` resets active state, counters and callbacks.

**FACT.** Activity markers never stop the recorder. Grace start is in `VoiceSessionController` after `BACKGROUND_RETURN`; new `VOICE_ACTIVITY_START` cancels it. Grace expiry is 1500 ms (`TURN_GRACE_WINDOW_MS`) and finalizes the M4A.

`LOCAL_SPEECH_*`, PCM RMS, `VoiceAudioCapture` and `LocalSpeechEnergyDetector` are not used by production `VoiceSessionController`.

## 7. Turn / Grace / Buffer

An acoustic turn starts on the first activity marker (`acousticTurnId++`) and ends after detector end hangover plus 1500 ms turn grace. Short pauses only cancel/restart grace. `BACKGROUND_RETURN` itself is not finalization.

At grace expiry, `activityInTurn=false`, current recorder is finalized, and next recorder begins. The resulting M4A is queued for sequential batch STT. `VoiceTurnBuffer.append()` trims and merges transcript text, but `processNextBatch()` calls `turnBuffer.finalizeNow()` immediately after every useful result; therefore one successful M4A generally becomes one submitted user turn. `VoiceTurnBuffer`'s own 1500 ms timer is scheduled by append but is cancelled by immediate `finalizeNow()`.

Useful means `text.trim().any(Char::isLetterOrDigit)`. Blank, whitespace-only, punctuation-only and STT errors are discarded; no USER message is sent for them. M4A deletion happens after STT callback, including result/error.

**RISK.** A thought that contains a pause long enough to cross detector hangover + 1500 ms grace is split into separate M4A/STT submissions. Conversely, activity during `WAITING_MODEL`/`SPEAKING` is still admitted to the same acoustic-turn machinery.

## 8. Barge-In

Actual chain:

`VOICE_ACTIVITY_START` → `onActivityStart()` → if `state == SPEAKING`, `confirmBargeIn()` → `BARGE_IN_DETECTED` → `TTS_STOP_REQUESTED` → `stopSpeech()` (`TTS_CLEAR`, `AUDIO_CLEAR`, `tts.clearCurrentUtterance()`, `playback.stop`) → actual main-handler `MediaPlayer.stopSafely()` + `release()` and MP3 deletion → `TTS_STOPPED`/`BARGE_IN_TTS_STOPPED` → state `LISTENING`, `OPENAI_CANCEL`, `onAssistantInterrupt()`.

**FACT.** Barge-in is only explicitly enabled in `SPEAKING`. It does not depend on transcript, STT or OpenAI. The activity event itself does not call MediaPlayer stop directly; it calls the stop path, whose actual player stop/release occurs in the posted callback. `bargeInConfirmed` prevents repeated barge-ins per response generation.

**FACT.** In the current code, `onActivityStart()` also runs in `WAITING_MODEL`, but no `confirmBargeIn()` branch exists there. Thus activity in `WAITING_MODEL` can cancel grace but does not stop TTS until state becomes `SPEAKING`.

**FACT.** Own TTS can cause `VOICE_ACTIVITY_START` because detector reads the same microphone recorder while playback is active. There is no TTS-aware mute, echo suppressor, acoustic echo cancellation implementation, or half-duplex gate in this path.

## 9. TTS and Self-Listening

Recorder remains active when TTS connects (`onModelRequestStarted`), when first playback starts, during all playback, after playback queue completion and until the next grace/stop. Detector remains active in `SPEAKING`. Speaker output can therefore enter the MIC capture and the resulting M4A; whether it does depends on device acoustics and route.

`MEDIA_RECORDER_AUDIO_SOURCE` production value is `1` (`MIC`). Value `7` (`VOICE_COMMUNICATION`) is only an unused A/B candidate constant. No production `VOICE_COMMUNICATION` source or verified echo suppression is present.

**FINDING.** Self-listening is structurally possible and can create `VOICE_ACTIVITY_START`; the supplied runtime evidence says activity did occur during `SPEAKING`. That proves detector activity during speaking, not whether every event was TTS or user speech.

When TTS finishes, `TtsPlaybackController` releases each player, deletes current MP3, and when queue/input are complete emits `PLAYBACK_COMPLETE`. `VoiceSessionController` emits `RETURN_LISTENING` and transitions to `LISTENING` if capture is ready. A barge-in stops/releases the player and clears queued temporary MP3 files.

## 10. Bluetooth / AirPods Routing

`MediaRecorderRouteController.begin()` sets `AudioManager.MODE_IN_COMMUNICATION`, registers `AudioDeviceCallback`, and calls `requestBestRoute("session_start")`. Input devices come from `getDevices(GET_DEVICES_INPUTS)`. Bluetooth input types accepted by code are `TYPE_BLUETOOTH_SCO`, numeric type 26 BLE headset, and numeric type 23 hearing aid. Built-in fallback is `TYPE_BUILTIN_MIC`. Output-only devices are not selected by this input list.

For API 31+, selected Bluetooth input is passed to reflective `AudioManager.setCommunicationDevice()`. For older APIs, `startBluetoothSco()` is attempted and `bluetoothScoStarted=true`; it is stopped when selected Bluetooth disappears or session stops. If a selected recorder exists and API >=24, `MediaRecorder.setPreferredDevice(selected)` is attempted. The Boolean from `setCommunicationDevice()` is ignored by caller.

Route confirmation is only emitted by `refresh()` when `recorder.routedDevice` is non-null. It writes `ROUTE_CHANGE_APPLIED` and `MEDIA_RECORDER_ROUTED_DEVICE` with `confirmed=true`, selected requested route and latency. `snapshot()` similarly derives from `routedDevice`; with null route it reports `confirmed=false` but does not create an applied event. `routedDevice` is the actual diagnostic source used by this controller; request success alone is not confirmation.

Device added/removed callbacks log `AUDIO_DEVICE_AVAILABLE`/`AUDIO_DEVICE_UNAVAILABLE`, then re-run route selection. Route request invokes the session callback except for `recorder_attached`. The Voice capture callback applies immediately when detector is inactive, otherwise sets `pendingRouteChange` and logs `ROUTE_CHANGE_DEFERRED`.

Scenarios:

- **A. Start without headphones:** `session_start` selects Bluetooth if it appears in input list, otherwise built-in. Built-in does not start SCO. Recorder attaches and `setPreferredDevice(builtIn)` is attempted. Actual route is only confirmed if `routedDevice` reports it.
- **B. AirPods connect during active session:** device-added callback selects a qualifying Bluetooth input. If speech/detector active, request is marked deferred; if silent, current recorder is stopped/deleted and recreated immediately. The requested device is re-applied to the new recorder. SCO is started only on pre-31; API 31+ uses communication device.
- **C. AirPods removed:** device-removed callback selects built-in. Existing Bluetooth SCO is stopped. Recreate is immediate during silence or deferred until next boundary during speech. New recorder receives built-in preferred device.
- **D. Start with AirPods already connected:** selection depends on AirPods being visible in `GET_DEVICES_INPUTS` under one of the accepted input types at `session_start`. If visible, it is selected and configured; if not, built-in is selected and the code relies on a later device-added callback. There is no explicit wait for SCO/route readiness before recorder start.

**RISK.** `setCommunicationDevice()` failure is not surfaced or used to force fallback. **RISK.** `routedDevice == null` produces no explicit negative route-confirmation event. **RISK.** Starting with already-connected AirPods can initially choose built-in if the Bluetooth input endpoint is not yet exposed; the later callback path is then required.

## 11. Dynamic Route Switching

`BUILT_IN → Bluetooth`: request changes selected/requested route. Silent detector path calls `applyRouteAtSafeBoundary()`, deletes current partial recorder, recreates recorder and restarts detector. Active speech path defers; at grace expiry the current M4A is finalized and the normal next-recorder creation uses the already requested route. `Bluetooth → BUILT_IN` is symmetric and stops SCO if it had been started.

Session state, `VoiceTurnBuffer`, GPT conversation, conversation id, TTS generation and model state are not reset. The detector is reset on recorder finish/recreate: its background returns to 200, active=false and timers/counters clear. `activityInTurn` is reset at grace expiry; route-only recreate does not itself reset `VoiceTurnBuffer`.

`pendingRouteChange` is cleared/logged at the turn boundary. The actual route request has already been made in `requestBestRoute`; the boundary recorder recreation is what gives the requested route a fresh recorder. There is no separate route-confirmed gate before returning to `LISTENING`; `MEDIA_RECORDER_STARTED` is sufficient.

**RISK.** After recreate the pipeline can be temporarily not-ready while UI ring remains active. **RISK.** Route request, preferred input and actual `routedDevice` can diverge; current state machine does not enter an explicit route-pending state.

## 12. STT

Production Voice STT is `XaiSttClient`, synchronous batch HTTP in a worker thread, endpoint `https://api.x.ai/v1/stt`, multipart `file`, separate `language` field defaulted and called as `ru`, with M4A generated by MediaRecorder. Response JSON field `text` is trimmed; blank text is an error and is discarded by session handling. No streaming STT, PCM upload or smart-turn query is used by `VoiceSessionController`.

Legacy/non-production streaming code exists in `XaiStreamingSttClient`, along with `VoiceAudioCapture` and `LocalSpeechEnergyDetector`. Repository reference search found no production reference from `VoiceSessionController`/`VoiceModeController` to `XaiStreamingSttClient`; the production session instantiates `XaiSttClient`. `VoiceInputController` also uses `XaiSttClient`, but that is manual mic.

## 13. Manual Mic

Manual mic is a separate `MicInputUiController` → `VoiceInputController` → `VoiceRecorder` → `XaiSttClient` path. It starts one `era_voice_<System.currentTimeMillis()>.m4a`, uses the same MIC/mono/16 kHz/AAC/MPEG-4/48 kbps profile, stops on second mic-button press, sends batch STT, puts returned text into `messageInput`, and deletes the file after success/error/cancel.

Shared classes are `XaiSttClient`, Android microphone permission and API key storage; both paths share the `MIC` MediaRecorder profile but not `MediaRecorderVoiceCapture`, amplitude detector, route controller, turn buffer, Voice state or TTS. MainActivity prevents starting manual mic while Voice Mode is active and prevents Voice Mode while manual recording is active.

**Migration impact: portable code; device-specific audio route/permission behavior.** A future Voice fix must avoid changing shared `XaiSttClient`, the common MIC profile or MainActivity mutual exclusion in a way that changes manual mic semantics.

## 14. Black Box Observability

Current Voice/TTS/route event vocabulary emitted by the inspected production path includes:

`VOICE_MODE_ON`, `VOICE_MODE_OFF`, `VOICE_STATE_CHANGED`, `VOICE_ERROR`, `MEDIA_RECORDER_PREPARED`, `MEDIA_RECORDER_STARTED`, `MEDIA_RECORDER_NEXT_START_GAP`, `MEDIA_RECORDER_STOPPED`, `M4A_FINALIZED`, `AMPLITUDE_SUMMARY`, `VOICE_ACTIVITY_START`, `BACKGROUND_RETURN`, `TURN_GRACE_STARTED`, `TURN_GRACE_CANCELLED`, `TURN_GRACE_EXPIRED`, `TURN_BUFFER_APPEND`, `TURN_BUFFER_FINALIZED`, `BATCH_STT_START`, `BATCH_STT_RESULT`, `TURN_SUBMIT`, `CHAT_MESSAGE`, `MEMORY_RETRIEVAL_START`, `MEMORY_RETRIEVAL_END`, `OPENAI_REQUEST_START`, `OPENAI_FIRST_DELTA`, `OPENAI_COMPLETED`, `OPENAI_ERROR`, `OPENAI_CANCEL`, `TTS_CONNECT_START`, `TTS_CLEAR`, `AUDIO_CLEAR`, `TTS_STOP_REQUESTED`, `TTS_STOPPED`, `BARGE_IN_DETECTED`, `BARGE_IN_TTS_STOPPED`, `TTS_SEND_FAILED`, `PLAYBACK_QUEUE_ADD`, `PLAYBACK_PREPARE_START`, `PLAYBACK_START`, `PLAYBACK_COMPLETE`, `PLAYBACK_ERROR`, `RETURN_LISTENING`, `AUDIO_DEVICE_AVAILABLE`, `AUDIO_DEVICE_UNAVAILABLE`, `ROUTE_CHANGE_REQUESTED`, `ROUTE_CHANGE_DEFERRED`, `ROUTE_CHANGE_APPLYING`, `ROUTE_CHANGE_APPLIED_BOUNDARY`, `ROUTE_CHANGE_APPLIED`, `MEDIA_RECORDER_ROUTED_DEVICE`, `PLAYBACK_ERROR`.

Events are useful for readiness (prepared/started/state), recorder gaps, activity/grace, STT boundary, barge-in request and actual player stop, TTS playback start/complete, and observed `MediaRecorder.routedDevice` when non-null.

**OBSERVABILITY GAP.** No explicit `MEDIA_RECORDER_RELEASED`, file-delete event, detector started/stopped event, recorder identity/sequence, route-confirmation-failed event, `setCommunicationDevice` result, SCO state callback, or actual input PCM/source identity is logged. `ROUTE_CHANGE_APPLIED` is not emitted when `routedDevice` is null. There is no event distinguishing TTS-caused activity from user speech, and no direct playback-to-activity correlation. The checked-in Black Box documentation still describes older `AudioRecord`/`AUDIO_INPUT_CONFIRMED`/`LOCAL_SPEECH_*` vocabulary, which does not match the current MediaRecorder production path.

## 15. Confirmed Runtime Facts

The named runtime JSON was not available in the repository. The following facts were explicitly supplied for this audit and are consistent with current code:

- `VOICE_ACTIVITY_START` occurred.
- Detector polling operated.
- Built-in route was confirmed.
- Recorder restart gaps were on the order of hundreds of milliseconds.
- Activity occurred during `SPEAKING`.
- `BARGE_IN_DETECTED` was present.

No exact timestamps, AirPods `deviceType`, requested route, `routedDevice` value, HTTP result or transcript from that file can be asserted here.

## 16. Findings

1. **FINDING — ready indication precedes readiness.** Ring starts before `MEDIA_RECORDER_STARTED`; static white bars never encode readiness.
2. **FINDING — detector is full-session, not listening-only.** It remains active during `WAITING_MODEL` and `SPEAKING`.
3. **FINDING — self-listening is not suppressed.** MIC source and continuous recorder allow TTS leakage into capture and activity detection.
4. **FINDING — route confirmation is conditional.** A non-null `MediaRecorder.routedDevice` is required for applied/confirmed logs; null and setter failure are not exposed as explicit failures.
5. **FINDING — documented route contract is stale.** Checked-in Black Box docs mention AudioRecord events, while current production uses MediaRecorder-specific events.

## 17. Risks

- AirPods may be available as output but absent/unready as an input device at session start; only accepted input types are selected.
- `setCommunicationDevice()` Boolean result is ignored, and no fallback/error is triggered from a false result.
- Pre-31 SCO start is asynchronous; recorder starts without waiting for an SCO-connected callback.
- Route changes require recorder recreate/deferred boundary and create measured capture gaps.
- `routedDevice` may remain null, leaving actual input route unconfirmed while UI transitions to `LISTENING`.
- A route request during activity is deferred in session policy, while the route controller has already changed its requested/preferred target.
- Recorder recreation resets amplitude background/detector state and can create a transient non-ready pipeline.
- TTS activity can become an acoustic turn and can be submitted as STT when grace expires.
- Successful STT results are finalized immediately, so VoiceTurnBuffer grace is not an additional multi-segment aggregation window.

## 18. Hypotheses

These are not proven by the available evidence:

1. **HYPOTHESIS — AirPods input endpoint/type mismatch.** On the affected device, AirPods microphone may not appear in `GET_DEVICES_INPUTS` as SCO/BLE/HA at the relevant time, causing built-in fallback.
2. **HYPOTHESIS — communication route and recorder preferred input diverge.** `setCommunicationDevice()` may fail/return false or select a communication route that `MediaRecorder.setPreferredDevice()` does not actually use.
3. **HYPOTHESIS — SCO timing.** On pre-31 or vendor-specific behavior, recorder may begin before SCO is connected and remain on built-in.
4. **HYPOTHESIS — recreate gap/state.** A dynamic AirPods callback may recreate the recorder, leaving a long gap or returning to a route not yet confirmed; the exact user-visible failure requires runtime events/device test.
5. **HYPOTHESIS — self-listening interference.** AirPods playback/phone speaker acoustics may generate detector activity, causing premature grace/finalization or barge-in; this cannot be separated from user speech with current events.

## 19. Known Good Behavior

**FACT.** Built-in MediaRecorder route can be confirmed by the current runtime instrumentation. Detector and activity events are produced. Recorder restart gaps are measured. Barge-in reaches `BARGE_IN_DETECTED` and the actual player stop/release callback. Batch Russian STT path is wired to xAI and successful text can reach OpenAI/TTS. The supplied passport reports good Russian recognition in prior testing, but does not prove AirPods input routing.

## 20. Known Broken Behavior

**Confirmed objective behavior:** ready-like ring can be visible before recorder readiness; detector continues during TTS; recorder can capture during TTS; route confirmation is absent when `routedDevice` is unavailable; current checked-in Black Box docs do not describe the current route event implementation.

**Not proven by this audit:** that the AirPods microphone is always routed incorrectly, that every `VOICE_ACTIVITY_START` during `SPEAKING` is TTS, or that a particular setter/SCO branch is the sole cause of the reported AirPods failure.

## 21. Open Questions

- On the affected AirPods device/API level, what exact input `AudioDeviceInfo.type` and `deviceName` are returned before and after session start?
- What are the return value and exception behavior of `setCommunicationDevice()`?
- Is `MediaRecorder.routedDevice` null, built-in, Bluetooth SCO, or another device immediately after each recorder start?
- On pre-31, when does SCO actually connect relative to `MEDIA_RECORDER_STARTED`?
- During an AirPods dynamic switch, what are `ROUTE_CHANGE_REQUESTED`, `ROUTE_CHANGE_DEFERRED`, `MEDIA_RECORDER_NEXT_START_GAP`, and next `MEDIA_RECORDER_ROUTED_DEVICE` values?
- Does the affected device produce activity with TTS-only playback and no user speech?
- Is the exact runtime file stored outside the repository/MediaStore and can it be supplied unchanged?

## 22. Recommended NEXT Decisions

No edits are made by this audit. Possible next repair-TZ options are:

- define a route-confirmation gate and a device/API-specific AirPods route test matrix;
- decide whether route setup must wait for confirmed communication/SCO before recorder start;
- decide how to handle `setCommunicationDevice()` failure and `routedDevice == null`;
- decide whether Voice Mode should remain full-duplex or introduce an explicitly specified TTS/self-listening policy;
- decide whether detector should remain active outside `LISTENING`, with explicit barge-in semantics for `WAITING_MODEL`;
- define recorder recreate/gap and route-pending state semantics;
- extend Black Box only after deciding the minimum required readiness, route, recorder-release and self-listening evidence;
- separately protect manual mic behavior with a regression test/verification checklist.

Build was intentionally not run and no production repair was attempted.
