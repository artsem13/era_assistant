# Era Interface Passport

## 1. Purpose

Этот паспорт описывает пользовательский интерфейс текущей версии Era. Отображаемое имя приложения — `Era`; launcher icon использует подготовленный `era_icon_launcher_foreground.png`, созданный из `era_icon_new.png`, через текущую adaptive/legacy icon-схему; startup splash реализован через `AppTheme` `android:windowBackground` и совместимый legacy drawable: чёрный фон с центрированным прозрачным `era_splash_foreground.png`, поскольку текущий проект использует compileSdk 29. Источник истины — текущие Kotlin/XML/resources в репозитории; `docs/passports/` — сверенная документация, а `docs/archive/` — только исторический контекст. Паспорт не описывает внутренние контракты OpenAI, памяти, поиска, Voice или Termux; для них используются отдельные паспорта.

## 2. UI Architecture

- `MainActivity` собирает главный экран, связывает действия пользователя с контроллерами и восстанавливает текущий диалог.
- `activity_main.xml` задаёт фон, чат, верхние controls, нижнюю панель ввода, scrim и боковое меню.
- `ConversationMessageViewFactory` создаёт строки сообщений; `ConversationViewportController` управляет безопасными отступами, fade-границами и прокруткой.
- `MicInputUiController` обслуживает ручную запись в поле ввода; `VoiceModeController`/`VoiceSessionController` обслуживают пользовательскую поверхность Voice Mode.
- `SearchStatusCardController` и `SearchPulseView` показывают временный статус Internet Search.
- `ResearchNoteController`/`ResearchNoteDialog` показывают и сохраняют заметку.
- `UsageActivity` и `UsageProviderController` реализуют экран использования; `BlackBoxActivity` — пользовательский экран локальной диагностики.
- Старые/keyguard/accessibility/voice-interaction компоненты зарегистрированы отдельно в manifest и не являются дополнительными экранными разделами главного UI. Debug-сборка отдельно регистрирует `TermuxDeviceTestActivity`.

## 3. Main Screen

Главный экран — полноэкранный `FrameLayout` с чёрной базой и изображением `@drawable/moon` на заднем плане (`centerCrop`). Поверх него находятся:

1. прозрачный `mainContent` с горизонтальными полями 12dp и верхним полем 8dp;
2. прокручиваемая область сообщений;
3. верхняя панель controls высотой 48dp;
4. нижняя панель ввода, закреплённая у нижнего края;
5. невидимые для accessibility верхняя и нижняя fade-границы чата;
6. при открытии меню — затемняющий scrim и выезжающая панель.

## 4. Top Bar

В `topControls` левая группа содержит `menuButton` и `noteButton`, а независимая правая группа закреплена у end родителя и содержит `micButton`/`voiceModeButton` (между ними постоянный spacing); `interruptButton` расположен перед ними и не сдвигает две голосовые кнопки.

- `menuButton` — круглая кнопка-гамбургер 48dp; открывает/закрывает side menu.
- `noteButton` — 48dp `ImageButton` с `ic_note_pen`; открывает диалог «Заметка».
- `blackBoxIndicator` — скрытый по умолчанию центрированный индикатор; при активной диагностике показывает `● REC mm:ss` красноватым жирным текстом.
- `micButton` — 48dp с `ic_mic`; запускает/останавливает разовую запись, после STT помещает текст в поле ввода.
- `voiceModeButton` — 48dp с `ic_voice_mode`; включает/выключает Voice Mode. Белые waveform bars видимы постоянно; bars и внешний ring пульсируют только при фактическом `LISTEN_READY`. Accessibility content description отражает состояния «выключен», «подключение», «слушаю», «обрабатываю», «говорю», «ошибка».
- `interruptButton` — скрытая кнопка `INTERRUPT`; появляется только во время воспроизведения ответа Voice Mode и останавливает текущую речь.

Кнопки имеют тёмную заливку `#1B1B1B`, радиус 24dp и обводку `#303030`.

## 5. Chat Area

`chatScrollView` — вертикальный `ScrollView` без полос прокрутки, с `clipToPadding=false`, внутри вертикальный `chatMessagesContainer`. При пустой истории добавляется приветствие «Начни разговор со Сферой.».

- User messages создаются `createUserMessage`: светлая плашка `#F2F2F2`, тёмный текст `#2A2A2A`, радиус 20dp, текст 16sp, выравнивание справа, максимум 84% ширины.
- Sphere messages создаются `createSphereMessage`: светло-серая плашка `#EAEAEA`, тёмный текст `#1C1C1E`, радиус 20dp, текст 16sp, выравнивание слева и ширина по горизонтальным границам поля ввода.
- Каждый сохранённый user/assistant bubble содержит вторичную метку локального времени `HH:mm`, вычисленную из сохранённого Unix timestamp в миллисекундах через system-default timezone. Для невалидных неположительных timestamp метка скрывается; восстановленные сообщения используют архивное время. Полная дата в bubble сейчас не показывается.
- У пузырей selectable text, многострочный режим, внутренние отступы 16dp/11dp и межстрочный интервал 3dp; строки имеют вертикальные отступы по 6dp.
- Ответ добавляется потоково: первый delta создаёт пустой Sphere bubble, последующие delta дописываются в него; после завершения текст фиксируется целиком.
- Ошибка добавляется отдельным полноширинным `TextView` с цветом `#FF8A80`.
- При восстановлении `ConversationRestoreController` загружает текущий conversation из архива и воспроизводит user/assistant сообщения. Если история есть, экран прокручивается вниз.
- Автопрокрутка включена, пока пользователь находится у низа (порог 48dp). Если пользователь ушёл вверх, потоковый ответ не меняет его позицию; принудительная прокрутка используется при открытии/закрытии search-card.
- Над и под областью чата создаются чёрные градиентные fade-слои длиной 96dp плюс высота соседних controls. Они не перехватывают touch и не доступны accessibility.

Во время поиска в начале контейнера временно показывается карточка 88dp `searchStatusCard`: `Ищу в интернете…`, `Поиск актуальной информации`, а также `Отмена` и анимированный `SearchPulseView`. Карточка скрыта в обычном состоянии; preview для её ручного показа виден только в debug-сборке.

## 6. Input Area

`inputPanel` закреплён снизу и имеет вертикальные отступы 10dp. `messageInput` — многострочный `EditText` с подсказкой «Спросить Эру…», максимумом 4 строки, минимумом 52dp, размером текста 16sp и тёмной rounded-формой `bg_message_input` (`#171717`, радиус 27dp, обводка `#292929`).

`sendApiButton` — прозрачный `ImageButton` 52dp с изображением `earth_send` и content description «Отправить». Нажатие сохраняет фокус/курсор в поле и отправляет непустой текст, если запрос не выполняется. При пустом поле показывается Toast. Во время запроса изображение пульсирует; после ответа или ошибки анимация останавливается.

## 7. Side Menu

Меню — `LinearLayout` шириной 260dp, расположенный сверху с отступом 64dp, с `bg_side_menu` (`#121721`, радиус 22dp) и elevation 12dp. Оно выезжает слева за 160ms; scrim `#66000000` затемняет экран и закрывает меню по нажатию. Кнопка Back также сначала закрывает меню.

Текущие пункты:

- «Инструкции» — редактор инструкций Сферы.
- «Модель: …» — выбор модели.
- «Использование» — `UsageActivity`.
- «Чёрный ящик» — `BlackBoxActivity`.
- «Выбрать API-ключ» — Android document picker для текстового файла OpenAI API key с persistable read permission.
- «Запустить ChatGPT» — проверяет Era accessibility service, при необходимости открывает системные настройки, затем запускает пакет `com.openai.chatgpt`.
- «Тест блокировки — 10 сек» — ставит exact alarm; Toast сообщает пользователю о десятисекундном окне для блокировки телефона.

В debug-сборке дополнительно видны разделитель и «Preview: Internet Search», который вручную показывает search-status card; в обычном UI этот пункт скрыт.

## 8. Dialogs and Overlays

- «Инструкции Сферы» — создаваемый в Kotlin тёмный `AlertDialog`: многострочное поле 320dp, сохраняющее `sphere_instructions` в `era_preferences`, и кнопки «Отмена»/«Сохранить».
- «Выбери модель» — тёмный `AlertDialog` со строками `Эконом — GPT-5 mini`/Mini (голубой), `Разговор — GPT-5.6 Luna`/Luna (фиолетовый), `Глубокий — GPT-5.6 Terra`/Terra (зелёный), `Максимум — GPT-5.6 Sol`/Sol (оранжевый). Текущая модель отмечена `●`; выбор сохраняется и отражается в меню.
- «Заметка» — `ResearchNoteDialog` с многострочным полем 240dp, подсказкой «Опиши здесь, что пошло не так…», кнопками «Отмена» и «Сохранить». Пустой текст не сохраняется; после создания Toast показывает локальное время `dd.MM.yyyy HH:mm`. Отдельного списка Notes нет.
- Экран Usage открывает дополнительный системный по стилю `AlertDialog` для ввода расчётного начального OpenAI balance.
- Меню и карточка поиска — overlays; scrim меню скрыт по умолчанию. Временные ошибки показываются красным текстом в чате, через `EditText.error` или Toast в зависимости от места ошибки.

## 9. Voice UI

Пользователь видит две поверхности: разовую запись через `micButton` и отдельный half-duplex Voice Mode через `voiceModeButton`. Разовая запись требует xAI key и `RECORD_AUDIO`; при успехе распознанный русский текст помещается в `messageInput`, где его можно отредактировать и отправить. Во время записи используется красное пульсирующее кольцо; при транскрипции кнопка отключается.

Voice Mode использует ту же кнопку, меняет её accessibility state, пульсирует при готовом слушании, показывает `INTERRUPT` только во время TTS и пишет ошибки в `messageInput.error`. Уход MainActivity с экрана останавливает активный Voice Mode. Внутренняя реализация описана в [VOICE.md](VOICE.md).

## 10. Notes / Research UI

Кнопка заметки доступна в верхней панели. Диалог сохраняет непустой текст через `ResearchNoteController` в research notes текущего conversation и связывает его с последним сообщением, если оно есть. Отдельного списка/экрана просмотра заметок или research в текущих layout не обнаружено.

## 11. Usage UI

`UsageActivity` — отдельный прокручиваемый тёмный экран с Back-кнопкой, вкладками `OpenAI` и `xAI`, переключением нажатием или горизонтальным swipe.

OpenAI показывает расчётный баланс, общие расходы, токены текущей сессии (входящие, cached, исходящие), стоимость, текущую модель и блоки Luna/Terra/Mini/Sol с токенами, долей и стоимостью. xAI показывает расчётную стоимость сессии, входящие/исходящие токены, сводку Internet Search и статический блок Voice. Значения берутся из локальных preferences и являются UI локального учёта, не официальным биллингом. Подробности — в [USAGE.md](USAGE.md).

## 12. Visual System

Подтверждённая ресурсами система минимальна: чёрный фон окна/системных панелей (`#000000`), фон главного экрана `moon`, тёмные поверхности `#121721`/`#151820`, светлый основной текст и приглушённый вторичный текст. Основные формы — прямоугольники с крупными скруглениями: 24dp для controls/menu, 27dp для input, 20dp для message bubbles, 14dp для search card.

Используются размеры 48dp для top controls, 52dp для input/send, 260dp для menu; типичные размеры текста — 16sp для сообщения/ввода, 15–18sp для заголовков/пунктов и 11–14sp для вспомогательных значений. Иконки — векторные `ic_mic`, `ic_note_pen`, `ic_voice_mode`; отправка использует bitmap `earth_send`. Дополнительные bitmap/background assets: `moon`, launcher assets и `era_loop_pingpong.mp4`; последнее не используется layout главного UI.

## 13. Navigation and User Flows

- Launcher → `MainActivity` → side menu → Instructions/Model/Usage/Black Box или внешние настройки/API picker.
- Main → note button → note dialog → save/cancel.
- Main → mic → permission/key picker/record → transcript in input → send.
- Main → Voice Mode → listening → model response/TTS → listening; interrupt доступен во время TTS.
- Main → menu → Usage → OpenAI/xAI page; Back возвращает предыдущий экран.
- Main → menu → Black Box → duration selection → activate/stop; Back возвращает на Main.
- Main → ChatGPT → проверка accessibility → системные настройки или запуск ChatGPT.
- Back на Main закрывает открытое меню, иначе завершает Activity.

## 14. UI State and Persistence

При запуске MainActivity восстанавливает текущий conversation и его сообщения из `ConversationArchive`; выбранная модель и инструкции берутся из `era_preferences`. Usage повторно читает локальные counters при `onResume`. Состояние side menu, текущая визуальная прокрутка и активный streaming bubble не являются заявленной persistent UI state. Реальные ключи хранятся как URI выбранных документов; интерфейс показывает только последствия их отсутствия/выбора.

Сообщения сохраняют timestamp в существующем поле `ConversationArchive.messages.timestamp` как `System.currentTimeMillis()`; восстановление не заменяет его текущим временем. Форматирование использует timezone телефона (`TimeZone.getDefault()` через локальное системное форматирование). `DeviceDateTimeContext` — отдельный runtime-контекст для prompt'ов, а не второй источник message timestamp.

## 15. Current Limitations / Incomplete UI

- Отдельного UI списка research notes нет; заметки только создаются через диалог.
- xAI Usage содержит статический текстовый блок Voice, а не отдельные интерактивные counters.
- Black Box и lock-screen test доступны пользователю из меню, но являются диагностическими/test surfaces, а не частью обычного chat flow.
- Debug search preview доступен только debug-сборке и не является production navigation item.
- Не заявлены отдельные UI-состояния сети или прогресса: ошибки поиска/ответа выводятся как текст/Toast, а ожидание OpenAI визуально обозначается пульсацией Moon.

## 16. Historical Differences

Проверенный `docs/archive/PASSPORT_2026-08-09.md` описывал более раннее состояние. По текущему коду не подтверждаются его утверждения, что ответы Сферы были обычным текстом без плашки: теперь они создаются в `#EAEAEA` bubble. Также user bubbles теперь выровнены справа и имеют максимум 84% ширины, тогда как архив описывал левое выравнивание. В текущем меню появился «Чёрный ящик», а Usage получил xAI-вкладку и xAI-сводку. Архивное описание «перехода» к тёмным диалогам также устарело: текущие редактор инструкций, selector модели и note dialog создаются сразу с тёмными custom views. Старые accessibility/keyguard эксперименты не считаются обычными экранами Main UI.

## 17. Source Map

Ключевые источники текущего интерфейса:

- [MainActivity.kt](../../app/src/main/java/com/era/assistant/MainActivity.kt)
- [ConversationMessageViewFactory.kt](../../app/src/main/java/com/era/assistant/core/ui/ConversationMessageViewFactory.kt)
- [ConversationViewportController.kt](../../app/src/main/java/com/era/assistant/core/ui/ConversationViewportController.kt)
- [SearchStatusCardController.kt](../../app/src/main/java/com/era/assistant/core/ui/SearchStatusCardController.kt) и `SearchPulseView.kt`
- [MicInputUiController.kt](../../app/src/main/java/com/era/assistant/core/voice/MicInputUiController.kt), `VoiceModeController.kt`, `VoiceSessionController.kt`
- [ResearchNoteController.kt](../../app/src/main/java/com/era/assistant/ResearchNoteController.kt), `ResearchNoteDialog.kt`
- [UsageActivity.kt](../../app/src/main/java/com/era/assistant/UsageActivity.kt), `UsageProviderController.kt`
- [BlackBoxActivity.kt](../../app/src/main/java/com/era/assistant/BlackBoxActivity.kt)
- [activity_main.xml](../../app/src/main/res/layout/activity_main.xml), [activity_usage.xml](../../app/src/main/res/layout/activity_usage.xml), [activity_black_box.xml](../../app/src/main/res/layout/activity_black_box.xml)
- `app/src/main/res/drawable/bg_*.xml`, `ic_mic.xml`, `ic_note_pen.xml`, `ic_voice_mode.xml`, `earth_send.png`, `moon.png`
- `app/src/main/res/values/colors.xml`, `strings.xml`, `styles.xml`
- `app/src/main/AndroidManifest.xml` и `app/src/debug/AndroidManifest.xml`
