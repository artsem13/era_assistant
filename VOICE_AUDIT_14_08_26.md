# VOICE AUDIT — 14.08.26

Дата аудита: 2026-08-14 UTC  
Режим: только чтение исходного кода, `HEAD` и текущего diff. Сборка, установка и исправления не выполнялись.

## Объём и исходное состояние

Полностью прочитан `AGENTS.md`. Канонический репозиторий: `/mnt/sdcard/Era/Era_From_Zip`.

Зафиксированный до создания этого отчёта `git status --short`:

```text
 M app/src/main/java/com/era/assistant/MainActivity.kt
 M app/src/main/java/com/era/assistant/core/MemoryItemStore.kt
 M app/src/main/java/com/era/assistant/core/ai/OpenAiStreamingClient.kt
 M app/src/main/java/com/era/assistant/core/ai/StreamingResponseController.kt
 M app/src/main/java/com/era/assistant/core/memory/RawBlockCoordinator.kt
 M app/src/main/java/com/era/assistant/core/voice/MicInputUiController.kt
 M app/src/main/java/com/era/assistant/core/voice/SimpleWebSocketClient.kt
 M app/src/main/java/com/era/assistant/core/voice/TtsChunker.kt
 M app/src/main/java/com/era/assistant/core/voice/TtsPlaybackController.kt
 M app/src/main/java/com/era/assistant/core/voice/VoiceModeController.kt
 M app/src/main/java/com/era/assistant/core/voice/XaiStreamingTtsClient.kt
 M app/src/test/java/com/era/assistant/ExampleUnitTest.kt
 M memory12_08_26.md
?? app/src/main/java/com/era/assistant/core/ai/StreamingRequestHandle.kt
?? app/src/main/java/com/era/assistant/core/memory/EmbeddingMath.kt
?? app/src/main/java/com/era/assistant/core/memory/MemoryContextBuilder.kt
?? app/src/main/java/com/era/assistant/core/memory/MemoryEmbeddingIndexer.kt
?? app/src/main/java/com/era/assistant/core/memory/MemoryEmbeddingStore.kt
?? app/src/main/java/com/era/assistant/core/memory/MemoryRetrievalSelector.kt
?? app/src/main/java/com/era/assistant/core/memory/OpenAiEmbeddingClient.kt
?? app/src/main/java/com/era/assistant/core/memory/SemanticMemoryRetriever.kt
?? app/src/main/java/com/era/assistant/core/voice/TtsExpressionProcessor.kt
?? app/src/main/java/com/era/assistant/core/voice/VoiceAudioCapture.kt
?? app/src/main/java/com/era/assistant/core/voice/VoiceAudioFocusController.kt
?? app/src/main/java/com/era/assistant/core/voice/VoiceModeConfig.kt
?? app/src/main/java/com/era/assistant/core/voice/VoiceModeState.kt
?? app/src/main/java/com/era/assistant/core/voice/VoiceSessionController.kt
?? app/src/main/java/com/era/assistant/core/voice/XaiStreamingSttClient.kt
```

На момент фиксации `git diff --stat` для tracked-файлов показывал 13 файлов, `688 insertions(+), 448 deletions(-)`. Untracked-файлы в эту статистику не входили. До создания отчёта рабочего diff относительно `HEAD` не было у самого отчёта, потому что он ещё не существовал.

Текущий `HEAD`: `76a12fa feat: add voice mode and streaming TTS`. Родитель: `d711008 Checkpoint: voice input and portable architecture`. Важное различие:

* `HEAD^ -> HEAD` добавил старый Voice Mode/TTS и в основном изменил UI ручного micButton, но не изменил recorder или batch STT.
* Незакоммиченный текущий diff поверх `HEAD` добавляет новый streaming STT, `AudioRecord`, state/session controller, audio focus и интеграцию Voice Mode с отправкой ответа.
* Большая часть текущих незакоммиченных изменений не относится к голосу и в этот аудит включена только там, где она меняет задержку пути Voice Mode до OpenAI.

## 1. Карта всего голосового тракта

| Файл | Роль | Ручной micButton | Voice Mode | Оба режима |
|---|---|---:|---:|---:|
| `app/src/main/java/com/era/assistant/MainActivity.kt` | UI wiring, permission/lifecycle, передача voice transcript в общий send path, передача OpenAI delta в TTS | Да | Да | Да |
| `app/src/main/res/layout/activity_main.xml` | Два отдельных элемента `micButton` и `voiceModeButton` | Да | Да | Да |
| `app/src/main/AndroidManifest.xml` | `RECORD_AUDIO`, `INTERNET`, voice-interaction services | Косвенно | Косвенно | Да |
| `app/src/main/java/com/era/assistant/core/voice/MicInputUiController.kt` | Нажатие ручного micButton, permission, UI, запуск/остановка записи | Да | Нет | Нет |
| `app/src/main/java/com/era/assistant/core/voice/VoiceInputController.kt` | Связывает manual UI с `VoiceRecorder` и `XaiSttClient` | Да | Нет | Нет |
| `app/src/main/java/com/era/assistant/core/voice/VoiceRecorder.kt` | Ручная запись через `MediaRecorder` в cache-файл | Да | Нет | Нет |
| `app/src/main/java/com/era/assistant/core/voice/XaiSttClient.kt` | Batch HTTP multipart `POST /v1/stt` | Да | Нет | Нет |
| `app/src/main/java/com/era/assistant/core/voice/VoiceModeController.kt` | Тонкая оболочка над новой Voice session | Нет | Да | Нет |
| `app/src/main/java/com/era/assistant/core/voice/VoiceSessionController.kt` | State machine, turn handling, barge-in, STT/TTS orchestration | Нет | Да | Нет |
| `app/src/main/java/com/era/assistant/core/voice/VoiceModeState.kt` | `OFF/CONNECTING/LISTENING/WAITING_MODEL/SPEAKING/ERROR` | Нет | Да | Нет |
| `app/src/main/java/com/era/assistant/core/voice/VoiceModeConfig.kt` | STT/TTS constants and barge-in thresholds | Нет | Да | Нет |
| `app/src/main/java/com/era/assistant/core/voice/VoiceAudioCapture.kt` | `AudioRecord`, PCM reads, RMS, AEC/NS | Нет | Да | Нет |
| `app/src/main/java/com/era/assistant/core/voice/XaiStreamingSttClient.kt` | Streaming STT WebSocket and transcript events | Нет | Да | Нет |
| `app/src/main/java/com/era/assistant/core/voice/VoiceAudioFocusController.kt` | Android transient music audio focus | Нет | Да | Нет |
| `app/src/main/java/com/era/assistant/core/voice/SimpleWebSocketClient.kt` | Raw TLS WebSocket text/binary transport for streaming xAI clients | Нет | Да | Нет |
| `app/src/main/java/com/era/assistant/core/voice/TtsChunker.kt` | Делит OpenAI text delta на TTS utterances | Нет | Да | Нет |
| `app/src/main/java/com/era/assistant/core/voice/TtsExpressionProcessor.kt` | Tags/emoji sanitization перед xAI TTS | Нет | Да | Нет |
| `app/src/main/java/com/era/assistant/core/voice/XaiStreamingTtsClient.kt` | xAI streaming TTS WebSocket | Нет | Да | Нет |
| `app/src/main/java/com/era/assistant/core/voice/TtsPlaybackController.kt` | Очередь MP3-файлов и `MediaPlayer` | Нет | Да | Нет |
| `app/src/main/java/com/era/assistant/core/voice/PulseRingAnimator.kt` | Пульс кольца Voice Mode | Нет | Да | Нет |
| `app/src/main/java/com/era/assistant/EraVoiceInteractionService.kt` | Отдельный Android VoiceInteractionService: событие и beep | Нет | Нет | Нет |
| `app/src/main/java/com/era/assistant/EraVoiceInteractionSessionService.kt` | Отдельный Android voice session service | Нет | Нет | Нет |
| `app/src/main/java/com/era/assistant/EraVoiceInteractionSession.kt` | UI этой platform voice session | Нет | Нет | Нет |
| `app/src/main/res/xml/voice_interaction_service.xml` | Manifest metadata для platform voice service | Нет | Нет | Нет |
| `app/src/main/res/drawable/ic_mic.xml` | Иконка manual micButton | Да | Нет | Нет |
| `app/src/main/res/drawable/ic_voice_mode.xml` | Иконка Voice Mode | Нет | Да | Нет |

`EraVoiceInteractionService` не использует `AudioRecord`, `MediaRecorder`, STT или TTS. Он только пишет event-файл и проигрывает короткий `ToneGenerator` beep, поэтому не является причиной рассматриваемого manual/Voice Mode audio capture поведения.

## 2. Текущий путь ручного micButton

Фактическая цепочка:

```text
micButton click
  -> MicInputUiController.handleMicButton()
  -> VoiceInputController.startRecording()
  -> VoiceRecorder.start()
  -> MediaRecorder.MIC
  -> MPEG_4 container + AAC, mono, 16 kHz, 48 kbps
  -> second micButton click
  -> VoiceRecorder.stop()
  -> cache/era_voice_<timestamp>.m4a
  -> XaiSttClient.transcribe() on a worker Thread
  -> multipart POST https://api.x.ai/v1/stt
  -> language=ru, file=<M4A>
  -> JSON field text
  -> runOnUiThread { messageInput.setText(text) }
```

Точные места:

* `MicInputUiController.kt:174-239` обрабатывает click. При отсутствии ключа сначала открывается file picker. При отсутствии permission запрашивается `RECORD_AUDIO` с request code `3001`.
* `MicInputUiController.kt:265-330` на втором нажатии синхронно вызывает `stopAndTranscribe`, отключает кнопку, затем принимает callback.
* `VoiceInputController.kt:21-39, 41-97` только связывает recorder и batch client.
* `VoiceRecorder.kt:20-83` создаёт `MediaRecorder`, задаёт `AudioSource.MIC`, MPEG-4/AAC, mono, 16000 Hz, 48000 bit/s, вызывает `prepare()` и `start()`.
* `VoiceRecorder.kt:118-162` вызывает `stop()`, освобождает recorder и проверяет непустой файл.
* `XaiSttClient.kt:28-274` читает xAI key из того же `xai_api_key_uri`, создаёт multipart POST на `/v1/stt`, отправляет сначала `language`, затем `file`, ждёт полный HTTP response и возвращает `root.optString("text")`.
* Для ручного пути нет `AudioRecord`, PCM, WebSocket, streaming STT, VAD, Smart Turn, RMS gate, AEC, NoiseSuppressor, AGC или AudioFocus.
* Ручной путь не отправляет аудио до остановки: запись сначала полностью закрывается и только затем загружается batch POST.

Параметры ручного xAI запроса: `Authorization: Bearer <key>`, `Content-Type: multipart/form-data`, `Accept: application/json`, multipart `language=ru` и поле `file` с MIME `audio/mp4` для `.m4a`. `XaiSttClient` не отправляет `model`, `sample_rate`, `encoding`, `vad_threshold` или Smart Turn query parameters.

Новые thread/очереди в этом manual path не появились. Есть один worker Thread для HTTP STT и возврат callback в UI thread, как было до текущего Voice Mode diff. Дополнительное время inherent для этого пути — финализация M4A в `MediaRecorder`, upload и server response; в коде нет нового ожидания VAD или Smart Turn.

## 3. Manual micButton: HEAD vs current

### Изменения `HEAD^ -> HEAD`

В коммите `76a12fa` ручной `MicInputUiController` действительно менялся, но изменения были UI-only:

* добавлен визуальный `ValueAnimator`/кольцо micButton с `Handler`;
* Toast-сообщения заменены на `messageInput.error`;
* добавлены `stopMicPulse()` при остановке и release.

`VoiceRecorder.kt`, `VoiceInputController.kt` и `XaiSttClient.kt` в `HEAD^ -> HEAD` не изменялись. Формат, источник, момент остановки и HTTP STT contract остались прежними.

### Незакоммиченный текущий diff относительно `HEAD`

Точный manual-related diff состоит из следующих изменений.

`MicInputUiController.kt`:

```diff
 private val messageInput: EditText
+private val isVoiceModeActive: () -> Boolean = { false }
```

```diff
 private fun handleMicButton() {
+    if (isVoiceModeActive()) {
+        showVoiceError("Сначала выключи Voice Mode")
+        return
+    }
```

```diff
+fun isRecording(): Boolean {
+    return voiceInputController.isRecording()
+}
```

`MainActivity.kt`:

```diff
 MicInputUiController(
     activity = this,
     micButton = micButton,
     messageInput = messageInput,
+    isVoiceModeActive = {
+        ::voiceModeController.isInitialized && voiceModeController.isActive()
+    }
 )
```

И дополнительно Voice Mode получает обратный callback:

```diff
 VoiceModeController(
     activity = this,
     voiceModeButton = voiceModeButton,
     messageInput = messageInput,
+    onVoiceMessage = { text -> sendTextToSphere(text, "voice") },
+    onAssistantInterrupt = { cancelActiveVoiceResponse() },
+    isManualMicRecording = {
+        ::micInputUiController.isInitialized && micInputUiController.isRecording()
+    }
 )
```

`VoiceRecorder.kt`, `VoiceInputController.kt`, `XaiSttClient.kt` и `activity_main.xml` в текущем diff не изменены. `VoiceAudioCapture.kt`, `XaiStreamingSttClient.kt`, AEC/NS и `VoiceAudioFocusController.kt` manual controller не вызывают.

Вывод: по коду manual micButton не начал использовать новый `AudioRecord`, новый PCM capture, VAD, Smart Turn или streaming STT. Изменилось только взаимное блокирование режимов: manual запрещён, пока `VoiceModeController.isActive()` возвращает `true`; Voice Mode запрещён, пока `isManualMicRecording()` возвращает `true`.

Существенный side effect возможен только на границе ресурсов: Voice Mode теперь владеет `AudioRecord`, AEC/NS и transient audio focus, а manual — `MediaRecorder`. Но UI не разрешает нормальный overlap. Доказательства прямого изменения manual audio quality в diff нет.

Текущий `MainActivity.kt` имеет 2541 строку против 2655 строк в `HEAD` (net `-114` строк; это в основном механическое уплотнение unrelated-кода и замена memory path). Новая voice wiring добавлена минимально в районе строк 412-437, send path — в 1715-1865, lifecycle — в 2319-2349 и 2503-2541.

## 4. Текущий streaming STT Voice Mode

Фактическая последовательность:

```text
VoiceModeButton
  -> VoiceSessionController.startSession()
  -> request AudioFocus
  -> startListening()
  -> create XaiStreamingSttClient
  -> client.connect() on worker Thread
  -> TLS WebSocket handshake to wss://api.x.ai/v1/stt
  -> server transcript.created
  -> XaiStreamingSttClient.ready = true
  -> onReady() posted to main Handler
  -> create VoiceAudioCapture
  -> AudioRecord.startRecording()
  -> read PCM chunks on EraVoiceAudioCapture thread
  -> RMS calculation + stt.sendAudio(binary frame)
  -> transcript.partial events
  -> is_final chunks accumulated by mergeTranscript()
  -> speech_final=true
  -> stopListening(), state WAITING_MODEL, onVoiceMessage(finalText)
```

Текущие параметры capture (`VoiceAudioCapture.kt:33-109`):

* sample rate: 16000 Hz;
* channel: `AudioFormat.CHANNEL_IN_MONO`;
* encoding: `AudioFormat.ENCODING_PCM_16BIT`, little-endian bytes as required by xAI `pcm`;
* source first: `MediaRecorder.AudioSource.VOICE_RECOGNITION`, fallback: `MediaRecorder.AudioSource.MIC`;
* buffer size: `maxOf(minBuffer * 2, 6400)` bytes. 6400 bytes at 16 kHz mono 16-bit = 3200 samples = 200 ms. Actual size can be larger when the platform minimum is larger;
* `AudioRecord.read(..., READ_BLOCKING)` is called with the full byte buffer;
* each successful read is copied and immediately passed to `onAudio`;
* no local silence timer and no local audio discard based on RMS.

`VoiceAudioCapture.rms()` only computes a number from every chunk. It does not alter or filter the byte array.

WebSocket URL (`XaiStreamingSttClient.kt:118-126`):

```text
wss://api.x.ai/v1/stt
?sample_rate=16000
&encoding=pcm
&interim_results=true
&language=ru
&smart_turn=0.7
&smart_turn_timeout=3000
&vad_threshold=0.08
```

Audio is sent as raw binary WebSocket frames. `SimpleWebSocketClient.sendBinary()` writes immediately once the socket output exists; it queues binary data only if the output is not ready. However, `XaiStreamingSttClient.sendAudio()` prevents that queue from being used until `ready=true`:

```kotlin
@Synchronized
fun sendAudio(audio: ByteArray) {
    if (!ready || intentionallyClosed || audio.isEmpty()) return
    socket?.sendBinary(audio)
}
```

Это важное подтверждённое поведение: audio before STT ready is discarded, not buffered by the STT client.

### Порядок microphone vs WebSocket

Текущий код делает:

```text
startListening()
  -> client.connect()
  -> wait for transcript.created
  -> onReady()
  -> create/start AudioRecord
```

То есть он не запускает microphone и WebSocket параллельно. Документированный xAI event flow также говорит ждать `transcript.created` перед отправкой audio, но это не означает ждать его перед началом локального capture. Текущая реализация ждёт его даже перед началом сбора samples.

Следствия:

1. Если пользователь начинает говорить во время DNS/TLS/WebSocket handshake или между `transcript.created` и фактическим выполнением `AudioRecord.startRecording()` на main thread, эти samples вообще не существуют в локальном буфере и потеряны.
2. Если по какой-либо гонке capture вызовет `sendAudio()` до `ready`, такой chunk будет молча отброшен.
3. Pre-roll buffer отсутствует.
4. Поэтому архитектура действительно может терять начало фразы. Это подтверждено порядком вызовов, а не предположено по субъективному ощущению.

После `onReady()` `VoiceAudioCapture.start()` вызывается через `mainHandler.post`. `startRecording()` затем вызывает `onStarted()` до запуска read thread; только после этого состояние меняется из `CONNECTING` в `LISTENING`. Это добавляет scheduling/start latency и создаёт узкое окно, в котором STT уже готов, а UI-state ещё не `LISTENING`.

## 5. xAI parameters, VAD, Smart Turn и thresholds

Официальные страницы xAI, проверенные 2026-08-14:

* [Speech to Text REST/WebSocket reference](https://docs.x.ai/developers/rest-api-reference/inference/speech-to-text)
* [Speech to Text model capability guide](https://docs.x.ai/developers/model-capabilities/audio/speech-to-text)
* [Text to Speech guide](https://docs.x.ai/developers/model-capabilities/audio/text-to-speech)
* [Voice REST reference](https://docs.x.ai/developers/rest-api-reference/inference/voice)

### Значения проекта

| Constant/parameter | Значение | Где используется | На что влияет |
|---|---:|---|---|
| `STT_SAMPLE_RATE` | `16000` | STT URL и `AudioRecord` | Формат/совместимость audio, не endpointing |
| `STT_ENCODING` | `pcm` | STT URL | Signed 16-bit PCM, не threshold |
| `STT_SMART_TURN_THRESHOLD` | `0.7` | `smart_turn=0.7` | Решение xAI, считать ли паузу концом turn |
| `STT_SMART_TURN_TIMEOUT_MS` | `3000` ms | `smart_turn_timeout=3000` | Server safety timeout для принудительного `speech_final` |
| `STT_VAD_THRESHOLD` | `0.08` | `vad_threshold=0.08` | Server speech-probability gate; ниже порога chunks могут быть skipped |
| `BARGE_IN_RMS_THRESHOLD` | `700.0` PCM RMS | только `VoiceSessionController` | Обновляет timestamp для подтверждения barge-in |
| `BARGE_IN_ENERGY_WINDOW_MS` | `1500` ms | только `VoiceSessionController` | Как давно должен быть громкий chunk для barge-in |
| `MIC_PULSE_*` | 140/140/320/450/1350 ms | manual UI animation | Только визуальный pulse |
| `TTS_STREAMING_LATENCY` | `1` | config constant; фактически URL hardcodes `1` | xAI TTS time-to-first-audio tradeoff |

В коде нет `STT_VAD_THRESHOLD`-gate на Android и нет другого local `VAD_THRESHOLD`. Нет `endpointing` query parameter, нет local silence timer и нет local Smart Turn implementation.

### Сверка с официальной документацией

* `sample_rate=16000` поддерживается.
* `encoding=pcm` соответствует signed 16-bit little-endian PCM.
* `interim_results=true` допустим; docs описывают partial примерно каждые 500 ms.
* `smart_turn` принимает confidence threshold от 0.0 до 1.0; `0.7` допустим и приведён xAI как пример.
* `smart_turn_timeout` — integer milliseconds, диапазон 1-5000; `3000` корректен.
* xAI описывает 0.5 как balanced, 0.7 как conservative, 0.9 как very conservative. Поэтому `0.7` не является ошибочным, но он намеренно менее быстрый на естественных паузах, чем balanced threshold.
* `vad_threshold` — speech-probability threshold 0.0-1.0; docs говорят, что меньшие значения пропускают более тихую или шумную речь, а `0` отключает gate.
* В текущих официальных страницах есть несогласованность: REST reference указывает default `0.08`, а model-capability page показывает `0.5` в одной таблице. Фактический проект явно отправляет `0.08`, поэтому для анализа следует опираться на реально отправляемое значение и учитывать эту документационную неоднозначность.
* `vad_threshold` по документации не меняет endpointing/`speech_final` timing; это отдельная причина не смешивать его с `smart_turn_timeout`.

Ответ на подозрение «0.08 требует говорить громче»: по официальному описанию — наоборот, низкий threshold должен принимать более тихую речь. Само значение `0.08` не доказывает причину необходимости говорить громче. Возможное влияние шумоподавления и Android audio source остаётся device-dependent, см. раздел 8.

## 6. Задержка окончания реплики

После последнего PCM chunk в проекте нет собственного wait такого вида `postDelayed`. Последовательность:

```text
последний chunk
  -> xAI VAD silence boundary
  -> Smart Turn evaluation at that boundary
  -> speech_final=true, если confidence >= 0.7
     или forced final after smart_turn_timeout=3000 ms
  -> transcript callback
  -> mainHandler.post
  -> mergeTranscript
  -> stopListening
  -> WAITING_MODEL
  -> onVoiceMessage
```

Потенциальные задержки:

* последний chunk может сам содержать до примерно 200 ms audio, а buffer может быть больше;
* server VAD/Smart Turn timing зависит от xAI и сети;
* `smart_turn=0.7` консервативнее balanced 0.5 и может удерживать turn на естественной паузе;
* `smart_turn_timeout=3000` ms — верхний server-side safety wait при низкой уверенности Smart Turn;
* callback ждёт очередь `mainHandler`, затем синхронную остановку `AudioRecord`/socket на main;
* после `speech_final` код не ждёт дополнительно перед `onVoiceMessage`, но общий send path может ждать semantic memory embedding до старта OpenAI SSE;
* OpenAI streaming и TTS WebSocket/TTS synthesis имеют свои сетевые задержки.

Точный максимальный пользовательский wait от тишины до auto-send нельзя вывести полностью из локального кода, потому что xAI VAD boundary, model evaluation и сеть внешние. Из явно заданного кода максимальный дополнительный Smart Turn safety wait — 3000 ms. Типичный wait может быть меньше, но его число кодом не зафиксировано.

Отдельно: после получения final transcript `MainActivity.sendTextToSphere()` запускает `SemanticMemoryRetriever.retrieve()` для непустой памяти. `SemanticMemoryRetriever.kt:48-98` делает отдельный blocking OpenAI embeddings request перед вызовом `sendMessageWithMemoryContext`; это новая внешняя задержка до OpenAI response и первого TTS request. Если активных memory items нет, retrieval возвращает сразу.

## 7. Потеря начала фразы: классификация

| Сценарий | Статус | Доказательство |
|---|---|---|
| Microphone стартует только после STT WebSocket `transcript.created` | **CONFIRMED** | `VoiceSessionController.kt:239-288`, capture создаётся в `onReady` |
| Audio chunks до `ready` drop/discard | **CONFIRMED** | `XaiStreamingSttClient.kt:48-52`: `if (!ready ...) return` |
| Capture start раньше готовности STT | **NOT SUPPORTED BY CURRENT ORDER** | В текущем пути capture вообще не создаётся до `onReady`; это всё равно означает потерю речи, сказанной до capture start |
| Pre-roll buffer | **CONFIRMED ABSENT** | Нет очереди samples в `VoiceAudioCapture` или `VoiceSessionController` |
| Пользователь начинает говорить во время handshake/handler scheduling | **CONFIRMED POSSIBLE** | В этот период `AudioRecord` ещё не читает |
| VAD не считает тихое начало speech | **POSSIBLE** | xAI server gate реально включён; `vad_threshold=0.08` низкий, поэтому собственным кодом это не доказывается |
| Local RMS gate для обычного listening | **NOT SUPPORTED BY CODE** | RMS только обновляет `lastSpeechEnergyAt`; bytes всегда передаются в `sendAudio` |
| Barge-in RMS gate режет обычный transcript | **NOT SUPPORTED BY CODE** | `BARGE_IN_RMS_THRESHOLD` не управляет `sendAudio` и не удаляет chunks |
| `CONNECTING` вместо `LISTENING` приводит к потере final | **POSSIBLE, narrow race** | `handleTranscript` принимает partial, но final отправляет только при `state == LISTENING`; переход в LISTENING происходит в `onStarted` |
| AudioRecord warm-up/start delay | **POSSIBLE/device-dependent** | `startRecording()` и запуск read thread происходят после `onReady`, измерения нет |
| AEC/NoiseSuppressor режут начало | **POSSIBLE/device-dependent** | Оба эффекта включаются без runtime quality measurement |
| Неправильный AudioSource | **POSSIBLE/device-dependent** | Сначала `VOICE_RECOGNITION`, fallback `MIC`; фактическое platform processing не видно из кода |
| Большой buffer/200 ms read cadence | **POSSIBLE for latency/drop under scheduling** | `max(minBuffer*2, 6400)`; exact device minBuffer неизвестен |
| Blocking binary WebSocket write тормозит capture thread | **POSSIBLE** | `sendAudio` вызывается из read thread, `sendBinary` пишет/flush синхронно под lock |
| WebSocket binary frame sender теряет ready audio | **NOT SUPPORTED after ready** | `sendBinary` queue/call path не содержит intentional drop, но network failure вызывает error |
| Transcript buffer reset перед late callback | **MITIGATED** | `sttGeneration` проверяется в Main Handler callbacks; `stopListening()` очищает buffer намеренно |
| Late callback предыдущей STT session | **MITIGATED BY CODE** | `generation != sttGeneration` callbacks discarded |

Главный подтверждённый путь потери начала — не local RMS, а то, что capture запускается слишком поздно и pre-roll отсутствует.

## 8. Почему может казаться, что нужно говорить громче

### Что делает RMS

`VoiceAudioCapture.kt:169-180` считает обычный signed PCM RMS для каждого chunk. В `VoiceSessionController.kt:245-251`:

```kotlin
if (level >= VoiceModeConfig.BARGE_IN_RMS_THRESHOLD) {
    lastSpeechEnergyAt = System.currentTimeMillis()
}
stt?.sendAudio(audio)
```

Это означает:

* RMS threshold применяется только к timestamp для barge-in;
* даже chunk ниже 700 отправляется в STT;
* нет gate, который не отправляет тихое аудио в обычном `LISTENING`;
* xAI получает весь PCM, который успел прочитать `AudioRecord`, независимо от local RMS;
* `BARGE_IN_RMS_THRESHOLD=700` может сделать тихую речь недостаточной для подтверждения interruption во время TTS, но не должен сам по себе обрезать обычную реплику.

### Android audio processing

`VoiceAudioCapture.kt:125-149` на `audioSessionId` созданного `AudioRecord` пытается включить:

* `AcousticEchoCanceler`, если `isAvailable()`;
* `NoiseSuppressor`, если `isAvailable()`.

`AutomaticGainControl` нигде не импортируется и не создаётся. В коде нет ручного gain/normalization.

AEC/NS применяются только к Voice Mode `AudioRecord`, не к manual `MediaRecorder` path. Они могут device-dependently подавить тихую речь или transient в начале, но из статического кода нельзя подтвердить конкретное ухудшение на устройстве.

### Вывод

Подтверждённой собственной причины «обычный Voice Mode STT принимает только громкую речь» нет: local RMS не gate-ит audio, а отправленный xAI threshold `0.08` официально описан как низкий threshold, допускающий более тихую речь. Подтверждённые/возможные факторы: речь до старта capture теряется; server VAD всё же может отвергнуть конкретный тихий chunk; AEC/NS и `VOICE_RECOGNITION` могут менять сигнал; barge-in отдельно требует RMS >=700 для interruption confirmation. Для manual micButton эта причина кодом не поддержана вообще, потому что manual использует отдельный `MediaRecorder` без этих эффектов.

## 9. Manual STT и Voice Mode: конфликт ресурсов

Ресурсная модель:

| Ресурс | Manual | Voice Mode |
|---|---|---|
| Recorder | `MediaRecorder.AudioSource.MIC` | `AudioRecord`, сначала `VOICE_RECOGNITION`, fallback `MIC` |
| Raw PCM | Нет, container output | Да, mono PCM16 |
| AEC/NS | Нет | Создаются на `AudioRecord.audioSessionId` |
| AGC | Нет | Нет |
| AudioFocus | Не запрашивается | `STREAM_MUSIC`, `AUDIOFOCUS_GAIN_TRANSIENT` |
| STT | Batch HTTP POST | Streaming WebSocket |

Защиты от overlap:

* manual click немедленно отказывается, если `voiceModeController.isActive()`;
* Voice Mode start отказывается, если `micInputUiController.isRecording()`;
* Voice Mode stop вызывает `capture.stop()` и `stt.stop()`;
* `VoiceAudioCapture.stop()` синхронно ставит `running=false`, останавливает/release recorder и эффекты; read thread после этого ещё может выполнить свой `finally`.

Потенциальные границы:

* `VoiceSessionController.release()` отправляет release через `mainHandler.post`, тогда как `MainActivity.onDestroy()` затем вызывает manual release; в teardown существует асинхронное окно, хотя обычный пользовательский overlap блокируется.
* Manual batch request уже после остановки recorder не владеет AudioRecord и не держит AudioFocus. Voice Mode не изменяет manual audio file или xAI batch parameters.
* После Voice Mode `onPause`/stop эффект и recorder обычно освобождаются, но статический код не гарантирует, что read thread уже полностью завершился к следующему мгновенному старту manual recorder.
* `AudioFocus` не общий с manual: это не доказательство, что focus сам меняет manual STT, но Voice Mode может влиять на системный audio route, пока активен.

Ответ: по штатным click paths manual и Voice Mode не должны одновременно захватывать microphone. Изменённый код создал явный mutual exclusion, а не общий recorder. Возможный transient race при остановке/teardown существует, но доказательства постоянного изменения manual audio quality нет.

## 10. State machine Voice Mode

| State | Кто/что приводит | Запускаемые ресурсы | Остановка/следующий переход |
|---|---|---|---|
| `OFF` | начальное состояние, toggle off, release | ничего | toggle -> permission/focus/start; error path может быть retried через start |
| `CONNECTING` | `startSession()` при permission, `startListening()` для обычного turn | STT client connect; пока нет `AudioRecord` | `transcript.created` -> capture start -> `LISTENING`; error -> `ERROR` |
| `LISTENING` | `VoiceAudioCapture.onStarted()` | `AudioRecord`, STT WebSocket, AEC/NS | `speech_final` -> stop listening + `WAITING_MODEL`; toggle -> `OFF`; error -> `ERROR` |
| `WAITING_MODEL` | final user transcript; model request begins | capture stopped; TTS socket/playback prepared; OpenAI/memory request | first prepared TTS file -> `SPEAKING`; no audio but playback input complete -> `LISTENING`; response error -> listening; toggle -> off |
| `SPEAKING` | `MediaPlayer.onPrepared` | TTS playback; barge-in STT starts on first audio/playback | confirmed barge-in -> stop TTS, cancel OpenAI, `LISTENING`; queue complete -> `LISTENING`; error -> `ERROR` |
| `ERROR` | STT/TTS/capture/audio focus/permission/pause failure | sessionActive=false; capture/TTS/focus released | another toggle can call startSession; no automatic reconnect |

Основные переходы по методам:

1. `startSession()` проверяет key, manual recording, permission и audio focus. При permission отсутствует он ставит `sessionActive=true`, `CONNECTING`, вызывает `requestPermissions()`.
2. `onRequestPermissionsResult()` при grant вызывает `startListening()`. В этом branch audio focus повторно не запрашивается и pulse не стартует — это отдельная state/focus inconsistency.
3. `startListening()` каждый раз делает `stopListening()`, увеличивает `sttGeneration`, очищает `utteranceText`, создаёт новый STT client и подключается.
4. `onReady()` создаёт `VoiceAudioCapture`; `onStarted()` переводит обычную session в `LISTENING`.
5. `handleTranscript()` игнорирует `is_final=false`, добавляет `is_final=true` в `utteranceText`, ждёт `speech_final=true`, затем auto-sends.
6. `onModelRequestStarted()` останавливает listening/speech, переводит в `WAITING_MODEL`, запускает playback и TTS WebSocket.
7. Первый TTS audio или playback start запускает отдельный STT для barge-in; state меняется в `SPEAKING` только на `MediaPlayer.onPrepared`.
8. После `onResponseCompleted()` TTS input помечается complete; когда playback queue опустеет, начинается новая STT listening session.
9. `onResponseFailed()` останавливает speech и начинает listening заново.
10. `onHostPause()` и ошибки переводят session в `ERROR`, останавливая capture, TTS и focus.

Подтверждённые race/robustness risks:

* обычный STT ready, capture start и UI transition разделены WebSocket thread, main Handler и capture thread;
* закрытие WebSocket до `transcript.created` не вызывает `onClosed`, потому что `onClosed()` репортит только когда `ready==true`; session может остаться в `CONNECTING` без error/reconnect;
* reconnect policy отсутствует;
* generation checks защищают callbacks предыдущей session, но не отменяют уже блокирующий socket write;
* state `WAITING_MODEL` зависит от memory retrieval/OpenAI/TTS, а не только от server STT;
* permission-grant branch обходит audio-focus request;
* `onResponseFailed()` предполагает, что session ещё активна, и перезапускает новый STT без backoff.

## 11. Barge-in

Путь:

```text
OpenAI response produced first TTS audio
  -> VoiceSessionController.onAudio callback
  -> startBargeInListeningIfNeeded()
  -> new STT WebSocket
  -> capture starts only after its transcript.created
  -> AudioRecord chunks compute RMS
  -> RMS >= 700 updates lastSpeechEnergyAt
  -> any nonblank STT text while SPEAKING and timestamp age <=1500 ms
  -> confirmBargeIn()
  -> stopSpeech()
  -> state LISTENING
  -> MainActivity.cancelActiveVoiceResponse()
```

Требуется одновременно:

* nonblank transcript event — partial `is_final` не требуется для initial confirmation;
* state `SPEAKING`;
* хотя бы один chunk с RMS `>=700` за последние 1500 ms.

При подтверждении:

* `tts.stop()` закрывает TTS socket;
* `playback.stop()` очищает queue and current MediaPlayer;
* `onAssistantInterrupt()` increments `sendGeneration`, cancels current OpenAI request и удаляет streaming message view;
* STT barge-in не закрывается в `confirmBargeIn`; он остаётся текущим listening STT и продолжает собирать user utterance.

Проблемы/границы:

* RMS не является подтверждением сам по себе, но тихая речь ниже 700 не сможет подтвердить barge-in, даже если STT partial уже есть.
* AEC включён, поэтому echo должен подавляться при поддержке устройства, но код не проверяет реальное качество эффекта.
* Собственный TTS теоретически может вызвать barge-in: если residual echo проходит AEC/NS и RMS >=700, а STT выдаёт nonblank text, код считает это человеческой речью. Защиты по playback output/reference нет.
* Barge-in STT не слушает до появления первого TTS audio/начала playback, поэтому пользовательская речь в gap между model start и первым audio не может interrupt.
* При barge-in `onAssistantInterrupt` отменяет OpenAI request, но новый user turn запускается только после `speech_final` текущей barge-in STT.

## 12. AEC, Noise Suppression, AGC, AudioSource и session id

В `VoiceAudioCapture.kt`:

* `AudioRecord` получает `audioSessionId` после создания;
* `AcousticEchoCanceler.create(audioSessionId)` и `NoiseSuppressor.create(audioSessionId)` вызываются до `startRecording()`;
* оба эффекта включаются через `.enabled = true` при наличии;
* при stop/failure оба release вызываются перед release `AudioRecord`;
* `AutomaticGainControl` отсутствует полностью;
* AudioSource — `VOICE_RECOGNITION`, fallback `MIC`;
* manual `MediaRecorder.MIC` не получает ни один из этих эффектов явно.

Что сегодняшние изменения могли ухудшить:

* новый Voice Mode path действительно вводит AEC/NS и другой AudioSource; это может менять тихий сигнал относительно manual path;
* `VOICE_RECOGNITION` может иметь device-specific platform processing, а код не логирует фактический gain/levels;
* нет AGC, поэтому тихая речь не компенсируется собственным кодом;
* нет pre-roll, поэтому initial consonants/transients могут отсутствовать до того, как effects/VAD увидят речь.

Но нельзя утверждать, что AEC/NS ухудшили именно сегодняшнюю manual STT: они не подключены к `VoiceRecorder`, а manual recorder code unchanged.

## 13. TTS path

Фактическая цепочка:

```text
MainActivity OpenAI response.output_text.delta
  -> VoiceModeController.onTextDelta()
  -> VoiceSessionController.mainHandler
  -> TtsChunker.append(delta)
  -> TtsExpressionProcessor.render(chunk)
  -> XaiStreamingTtsClient.speak(text)
  -> SimpleWebSocketClient text.delta + text.done
  -> xAI audio.delta events (base64 JSON)
  -> accumulate one utterance in ByteArrayOutputStream
  -> xAI audio.done
  -> TtsPlaybackController.enqueue(full MP3 bytes)
  -> temporary .mp3 in cache
  -> MediaPlayer.prepareAsync()
  -> playback starts
```

Details:

* `onModelRequestStarted()` calls `playback.start()` and starts TTS socket before OpenAI text exists.
* Current TTS URL is `wss://api.x.ai/v1/tts?language=ru&voice=eve&codec=mp3&optimize_streaming_latency=1`. `1` is valid xAI latency optimization; the config constant has the same numeric value, but the URL currently hardcodes it rather than reading `VoiceModeConfig.TTS_STREAMING_LATENCY`.
* First TTS request is not sent at model start. It waits for an OpenAI delta that causes `TtsChunker` to emit a chunk. A sentence shorter than 220 chars emits on punctuation; otherwise the chunker may wait for punctuation/space at the max length.
* `XaiStreamingTtsClient` sends each utterance as `text.delta` immediately followed by `text.done`, and counts outstanding utterances.
* Every xAI `audio.delta` is base64-decoded and accumulated. Playback does not start per audio delta; it waits for `audio.done` and writes the complete utterance to a temp MP3 file. This is a real latency layer despite use of a streaming WebSocket.
* `TtsPlaybackController` queues files and creates one `MediaPlayer` at a time. `onPlaybackStarted` is called on each prepared file, not only once per response.
* `audio.done` decrements outstanding utterances. After OpenAI completion, `finishInput()` plus zero outstanding utterances calls `playback.markInputComplete()`.
* Queue completion waits until input is complete, player is null and queue empty; then `VoiceSessionController` stops any barge-in listening and starts a fresh listening STT session.
* `stop()` clears queue, stops/releases player on main Handler, closes the old TTS socket and session-token callbacks are checked against `activeTtsToken`.
* `SimpleWebSocketClient` supports text and binary frames, but does not implement continuation/fragmented WebSocket frames. The code assumes each received JSON event is one complete text frame. This is an unverified transport robustness risk, not a confirmed cause from the static code.

Additional delay before first TTS audio can come from the current `MainActivity` send path:

```text
speech_final
  -> onNewRequest / TTS socket starts
  -> semantic memory embedding request (when active memory exists)
  -> OpenAI HTTP SSE starts
  -> enough deltas for TtsChunker boundary
  -> xAI TTS synthesis
  -> full audio.done buffer
  -> MediaPlayer prepareAsync
```

The memory request is parallel only with TTS socket setup; it is before OpenAI text and therefore before the first TTS request.

## 14. Expressive TTS

Фактический inline allow-list в `TtsExpressionProcessor.kt:9-12`:

```text
[pause] [long-pause] [hum-tune]
[laugh] [chuckle] [giggle] [cry]
[tsk] [tongue-click] [lip-smack]
[breath] [inhale] [exhale] [sigh]
```

Фактический wrapping allow-list в `TtsExpressionProcessor.kt:14-17`:

```text
<soft> <whisper> <loud> <build-intensity> <decrease-intensity>
<higher-pitch> <lower-pitch> <slow> <fast>
<sing-song> <singing> <emphasis>
```

Emoji mapping:

```text
😮‍💨 -> [sigh]
😂 -> [laugh]
🤣 -> [laugh]
😅 -> [chuckle]
😢 -> [cry]
😭 -> [cry]
😔 -> [sigh]
```

Sanitization:

* known inline tags are normalized to lowercase `[tag]`;
* matching known wrappers are preserved only with correct nesting;
* unknown/malformed markup is omitted;
* unmatched wrapping opener is removed;
* unsupported emoji codepoints are removed from plain text, except mapped emoji are converted first;
* runs of 3+ dots and ellipsis receive `[pause]`;
* repeated spaces/tabs collapse.

По текущей официальной xAI TTS documentation list содержит также `<laugh-speak>`. В текущем local allow-list его нет, поэтому expressive layer уже уже официального полного списка. Это не влияет на STT или microphone path, потому что processor вызывается только после OpenAI output delta.

## 15. MainActivity voice wiring и lifecycle

Текущие voice-related участки:

* `MainActivity.kt:377-437`: получает два разных buttons; создаёт manual controller, затем Voice Mode controller.
* `MainActivity.kt:417-419`: manual controller получает только read-only `isVoiceModeActive` closure.
* `MainActivity.kt:430-434`: Voice Mode получает `sendTextToSphere(text, "voice")`, interrupt callback и read-only manual recording status.
* `MainActivity.kt:1715-1778`: общая отправка теперь принимает `source`; voice source сохраняется в archive и при отсутствии OpenAI key вызывает `voiceModeController.onResponseFailed()`.
* `MainActivity.kt:1732`: `voiceModeController.onNewRequest()` вызывается для общего text/voice send path, но session внутри делает no-op, если Voice Mode off.
* `MainActivity.kt:1801-1815`: каждый OpenAI delta передаётся Voice Mode TTS и UI.
* `MainActivity.kt:1817-1843`: completion вызывает `onResponseCompleted()` TTS.
* `MainActivity.kt:1846-1861`: error вызывает `onResponseFailed()`.
* `MainActivity.kt:1867-1876`: barge-in cancels the current OpenAI request through generation/request handle.
* `MainActivity.kt:2319-2349`: сначала manual permission request `3001`, затем Voice Mode `3002`; request codes различаются.
* `MainActivity.kt:2503-2509`: `onPause()` cancels active voice response and posts Voice Mode host pause error.
* `MainActivity.kt:2511-2541`: release Voice Mode then manual controller on destroy.

Главный side effect для Voice Mode — не micButton recorder, а общий send path. В `HEAD` он уже имел memory topic routing, но current dirty diff заменил его на `SemanticMemoryRetriever`: при активной memory перед OpenAI request добавляется blocking embedding HTTP call. Поэтому Voice Mode может стать заметно медленнее после final transcript даже при исправном STT.

Ни `activity_main.xml`, ни `AndroidManifest.xml` не изменились в текущем diff. Permission `RECORD_AUDIO` был уже объявлен. MainActivity changes не передают manual audio bytes в Voice Mode.

## 16. Человеческий список изменений относительно HEAD

### `MainActivity.kt`

**Было:** manual controller создавался с `activity`, `micButton`, `messageInput`; Voice Mode controller имел только эти три аргумента и был старым TTS wrapper. Общий send function принимал только текст.  
**Стало:** manual получает Voice Mode activity guard; Voice Mode получает callback для voice transcript, interrupt и manual recording; send path различает `text`/`voice`, отменяет generation/request, передаёт OpenAI deltas в session и останавливает/перезапускает listening по callbacks.  
**Возможное следствие:** manual audio format не изменился, но режимы теперь взаимно блокируются. Voice response появился на streaming STT path. Semantic memory retrieval теперь может задержать OpenAI/TTS.

### `MicInputUiController.kt`

**Было:** manual controller всегда обрабатывал click по старой цепочке.  
**Стало:** при активном Voice Mode click отказывается; добавлен `isRecording()` для обратной проверки из Voice Mode.  
**Возможное следствие:** manual button не стартует во время активной Voice session. Это изменение поведения UI, не audio capture.

### `VoiceModeController.kt`

**Было:** `enabled` flag, TtsChunker, TTS и playback; Voice Mode не владел streaming STT/AudioRecord/state machine.  
**Стало:** controller делегирует в новый `VoiceSessionController`, получает callbacks и state/session lifecycle.  
**Возможное следствие:** появился полный capture/STT state machine, deferred microphone start, AEC/NS, Smart Turn, barge-in и новые races.

### Добавленные untracked voice files

**Было:** в `HEAD` отсутствовали `VoiceSessionController`, `VoiceAudioCapture`, `XaiStreamingSttClient`, `VoiceModeConfig`, `VoiceModeState`, `VoiceAudioFocusController`, `TtsExpressionProcessor`.  
**Стало:** эти компоненты реализуют streaming STT, PCM capture, server VAD/Smart Turn config, AEC/NS, focus, barge-in and expressive TTS.  
**Возможное следствие:** capture не стартует до STT ready; no pre-roll; `vad_threshold` и Smart Turn влияют только Voice Mode, но AEC/NS/AudioSource отличаются от manual.

### `SimpleWebSocketClient.kt`

**Было:** text-frame transport для TTS.  
**Стало:** text + binary frames, pending binary queue, binary receive callback.  
**Возможное следствие:** позволяет raw PCM STT; synchronous `sendBinary` выполняется из capture read thread и может быть чувствителен к network blocking.

### `TtsChunker.kt`

**Было:** sentence/punctuation/length chunking без wrapper depth.  
**Стало:** punctuation/space boundaries учитывают wrapping tags; max chunk остаётся 220.  
**Возможное следствие:** expressive wrapper может отложить первый chunk до closing tag/length; это TTS delay, не STT.

### `TtsPlaybackController.kt`

**Было:** очередь и MediaPlayer без playback lifecycle callbacks.  
**Стало:** callbacks `onPlaybackStarted`, `onPlaybackQueueCompleted`, errors, input-complete bookkeeping.  
**Возможное следствие:** Voice Mode переключает state и запускает новый STT только после полного playback queue.

### `XaiStreamingTtsClient.kt`

**Было:** basic stream start/speak/stop, audio buffer and error.  
**Стало:** session tokens, outstanding utterances, input completion, stale callback filtering, `optimize_streaming_latency=1`.  
**Возможное следствие:** корректнее cancellation, но audio still buffers until each `audio.done`; first TTS request waits for TtsChunker output.

### Файлы, которые могли бы прямо изменить manual audio, но не изменились

`VoiceRecorder.kt`, `VoiceInputController.kt`, `XaiSttClient.kt`, `activity_main.xml`, `AndroidManifest.xml` — current `git diff` относительно `HEAD` пуст. Поэтому перехода manual path на new capture или новые STT parameters не произошло.

## 17. Root-cause table

| Problem | Cause/evidence | Confidence |
|---|---|---|
| Manual STT стал медленнее | Direct manual recorder/STT files and parameters are unchanged. Current diff adds only Voice Mode guard and recording-status callback; no new VAD/AEC/AudioRecord/streaming layer in manual path. Possible transient recorder contention during asynchronous Voice Mode teardown, but not proven. | **HIGH that no direct code cause exists; LOW for transient resource hypothesis** |
| Начало речи пропадает | `VoiceSessionController` connects WebSocket first and creates `AudioRecord` only from `onReady` after `transcript.created`; no pre-roll. Speech before that moment is not captured. `sendAudio` also explicitly drops data while `ready=false`. | **HIGH** |
| Нужно говорить громче в normal listening | No local amplitude gate: all PCM chunks go to STT. `vad_threshold=0.08` is low and official docs describe lower values as accepting quieter speech. AEC/NS/VOICE_RECOGNITION can device-dependently change quiet signal; no measurement proves it. | **HIGH that local RMS is not cause; MEDIUM/LOW for effects/VAD device hypothesis** |
| Нужно говорить громче for barge-in | Barge-in confirmation requires RMS >=700 in a 1500 ms window plus nonblank STT text. Quiet speech can fail interruption confirmation. This does not gate ordinary listening. | **HIGH, limited to barge-in** |
| Отправка после конца реплики задерживается | Smart Turn threshold 0.7 is conservative; forced safety timeout is 3000 ms. No local timer, so exact typical xAI wait is external. Large >=200 ms chunks and main-thread stop add local latency. | **HIGH** |
| Transcript обрывочный | Initial capture gap and server VAD are supported causes. `mergeTranscript` can also join `is_final` chunks heuristically and may duplicate/retain wrong boundaries if xAI sends non-cumulative chunk-final text, but it does not explain all missing first words by itself. | **MEDIUM** |
| Voice Mode нестабилен | No reconnect for a close before `transcript.created`; that close is not reported because `onClosed` only reports when `ready=true`. Multiple worker/main/capture threads, synchronous WebSocket writes, repeated STT reconnect per turn, TTS full-utterance buffering and mandatory memory embedding before OpenAI add separate failure/latency points. | **HIGH for concrete risks; MEDIUM for observed symptom attribution** |
| Первый ответ Voice Mode задерживается | After final transcript, current MainActivity can perform a blocking OpenAI embedding request for semantic memory before starting OpenAI SSE. TTS then waits for an OpenAI text chunk, a TTS boundary, xAI synthesis and MediaPlayer prepare. | **HIGH when active memory items exist; MEDIUM otherwise** |

## 18. Минимальный план будущего исправления — без выполнения сейчас

1. **Сохранить manual path изолированным.** Не переводить `VoiceRecorder` на `AudioRecord`, не добавлять в него Voice Mode AEC/NS/VAD/Smart Turn и не менять batch `POST /v1/stt`. Оставить взаимное исключение, но сделать release/ownership границу проверяемой и синхронной.
2. **Устранить initial capture gap в Voice Mode.** Запускать capture и WebSocket initialization параллельно, хранить небольшой bounded pre-roll и отправлять его после `transcript.created`; не делать silent drop до ready. Проверить, что generation/session не смешивают старые chunks.
3. **Разделить endpointing и loudness diagnosis.** Не использовать `BARGE_IN_RMS_THRESHOLD` как normal STT gate. Отдельно измерить raw RMS до/после AEC/NS и server VAD; затем минимально выбрать documented Smart Turn/VAD/endpointing parameters. Для быстрого conversational turn сначала сравнить balanced Smart Turn с текущим conservative 0.7, не меняя manual STT.
4. **Уменьшить задержки streaming path.** Проверить фактический AudioRecord chunk duration и network write blocking; рассмотреть real-time chunks ближе к xAI example около 100 ms. Для TTS измерить time-to-first-delta, first chunk boundary, `audio.done` и MediaPlayer prepare; не считать накопление полного MP3 utterance «нулевой» задержкой.
5. **Закрыть state-machine races.** Обработать early WebSocket close before ready, permission-grant audio focus branch, reconnect/error policy и lifecycle cancellation. Отдельно решить, должен ли semantic memory retrieval быть optional/non-blocking для voice response.

## 19. Что нельзя трогать при исправлении

* Не менять `VoiceRecorder`/`XaiSttClient` manual contract только ради Voice Mode.
* Не добавлять AEC/NS, RMS gate или Smart Turn в ручной micButton без отдельного требования и измерений.
* Не считать barge-in threshold обычным STT threshold.
* Не смешивать manual `MediaRecorder` и Voice Mode `AudioRecord` ownership.
* Не удалять generation/session guards и TTS cancellation без замены эквивалентной защиты от late callbacks.
* Не переписывать весь `MainActivity`; будущие изменения должны оставлять его только wiring-слоем.
* Не менять Gradle/SDK/AAPT2/build environment для этой runtime-аудио проблемы.

## EXECUTIVE SUMMARY

1. Старый ручной микрофон по коду не был переведён на новый тракт: он всё ещё пишет отдельный M4A через `MediaRecorder` и отправляет batch `POST /v1/stt`. Текущий diff добавил только запрет overlap с Voice Mode. Если manual стал хуже, прямой причиной этот diff не является; остаётся только возможный краткий конфликт освобождения системного микрофона при переключении режимов.

2. Voice Mode задерживается потому, что он ждёт готовности WebSocket STT перед запуском микрофона, потом может ждать Smart Turn до 3 секунд, затем при активной памяти делает дополнительный embedding request, а TTS ждёт границу текста, полное `audio.done` и подготовку `MediaPlayer`.

3. Первые слова могут пропадать, потому что речь, сказанная до `transcript.created` и фактического старта `AudioRecord`, нигде не сохраняется. Pre-roll отсутствует, а `sendAudio()` при `ready=false` просто отбрасывает chunk.

4. Необходимость говорить громче не объясняется local RMS для обычного listening: он не фильтрует аудио. Для barge-in громкость действительно важна из-за RMS 700. Для обычного STT остаются возможные device-dependent AEC/NS, `VOICE_RECOGNITION` processing и server VAD; отправленное значение `0.08` официально скорее помогает тихой речи, чем требует её усиливать.

5. Наиболее вероятные будущие изменения: сохранить manual STT отдельно; добавить parallel capture + bounded pre-roll; измерить и развести VAD/Smart Turn/barge-in thresholds; уменьшить/проверить audio chunk and TTS buffering latency; закрыть early-close, permission/focus и lifecycle races.

6. При исправлении нельзя трогать manual recorder/batch STT без отдельного доказательства, нельзя переносить Voice Mode AEC/NS/VAD в manual path, нельзя использовать barge-in RMS как обычный speech gate и нельзя убирать session/generation protection.

## Итог аудита

После создания этого файла не выполнялись исправления, автоматические форматтеры, сборка, commit, push, reset, checkout, restore или revert. В рамках аудита создан только этот разрешённый текстовый отчёт.
