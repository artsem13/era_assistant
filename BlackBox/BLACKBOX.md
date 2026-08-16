# Era Black Box

## Назначение

Black Box — постоянный механизм технической диагностики Era. `Profile` описывает подсистему, `Session` — одну явно активированную пользователем запись, `Event` — одно событие timeline. Версия 0.1 поставляет профиль `VOICE_TTS`.

Главный инвариант: inactive = zero logging. До активации controller не создаёт файл, timestamp, очередь, историю событий или сетевую диагностику; `BlackBoxController.log()` сразу возвращается. В ACTIVE-сессии профиля VOICE_TTS разрешено диагностическое логирование полного текста STT и chat-сообщений через тот же асинхронный writer.

## Lifecycle

На экране «Чёрный ящик» выбирается VOICE_TTS и preset 1/5/10/30 минут. `activate()` создаёт новый sessionId, имя файла, metadata, monotonic start (`SystemClock.elapsedRealtime()`) и writer. Countdown вычисляется controller, UI только отображает snapshot. Завершение происходит по timer или `USER_STOPPED`; writer закрывает JSON и публикует файл. Предусмотрены `TIMER_FINISHED`, `USER_STOPPED`, `APP_SHUTDOWN`, `ERROR`.

Controller является process-scoped Kotlin `object`, поэтому pause/resume/recreate Activity не создаёт новую сессию. MainActivity не завершает Black Box при своей recreation. Остановка приложения с уничтожением процесса может оставить последний JSON незакрытым; writer периодически flush-ит уже записанные события.

## Storage

На Android 10+ runtime data находится в MediaStore: `Download/Era/BlackBox/`. Файл получает уникальное имя `yyyy-MM-dd_HHmmss_voice_tts_<sessionId>.json` и становится видимым после закрытия (`IS_PENDING=0`). На старых версиях используется app-external `Download/Era/BlackBox` directory. Git-репозиторий и conversation archive не используются.

`BlackBoxController` управляет состоянием и timer, `BlackBoxSession` — monotonic lifecycle/correlation, `BlackBoxWriter` — последовательной записью, `BlackBoxStorage` — переносимым runtime target, `BlackBoxSanitizer` — redaction, `BlackBoxActivity` — UI.

События ставятся в single writer executor; main/audio/WebSocket callbacks не делают blocking file IO. Flush выполняется после header и каждых 20 events, а также при stop.

Для Voice Mode `AUDIO_ROUTE_REQUESTED` означает только попытку выбрать preferred communication input. Фактический источник PCM фиксируется отдельно через `AUDIO_INPUT_CONFIRMED` и последующие `AUDIO_ROUTE_CHANGED`: компонент читает observed route активного `AudioRecord`, а не подставляет requested device. При исчезновении Bluetooth пишется `AUDIO_ROUTE_FALLBACK`, затем requested built-in route; на Android, где physical route недоступен для наблюдения, сохраняются `confirmed=false` и `inputType=UNKNOWN`. Логируются только безопасные тип, product name (если доступен), флаги Bluetooth/built-in, результат выбора и причина; MAC, Bluetooth identifiers и raw audio не сохраняются.

Local speech detector не открывает второй микрофон: он получает копию того же PCM chunk, который передаётся в xAI STT. RMS сравнивается с адаптивным noiseFloor, с hysteresis, start stability и end hangover. В JSON пишутся только `LOCAL_SPEECH_START` и `LOCAL_SPEECH_END` с агрегированными `rms`, `noiseFloor`, `relativeLevel` и route snapshot, без raw PCM.

## Security and limits

Санитизируются поля с api key, authorization, password, cookie, credential, secret и bearer, а также bearer/key-подобные фрагменты в exception text. API keys, headers, credential URI и raw audio по-прежнему не записываются. В ACTIVE-сессии VOICE_TTS теперь записываются полные STT-транскрипты и фактические пользовательские и ассистентские chat-сообщения; содержимое текстовых событий не обрезается и защищается только от явно распознаваемых credential-фрагментов. Допустимы character counts, turn/generation, socket state, close code/reason и технические aggregate values. v0.1 не имеет history browser, export/import и crash reporter.

## UI and transfer

Пункт меню и screen используют тёмные цвета, карточки и spacing экрана Usage. В inactive состоянии верхний центр MainActivity пуст. При active там показывается компактный `● REC mm:ss`. Для передачи инженеру нужно остановить запись, дождаться «Запись завершена» и передать последний файл из `Download/Era/BlackBox/` вместе с описанием воспроизводимого симптома.

## Новый profile

Добавьте значение `BlackBoxProfile`, определите его event vocabulary и документацию, затем подключите `BlackBoxController.log()` в существующие точки исполнения. Не добавляйте проверки active в вызывающий код: inactive no-op уже инкапсулирован в controller. Не сохраняйте секреты, raw media или conversation archive.
