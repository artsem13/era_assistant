# ERA UI AUDIT — 14.08.26

## Scope и исходное состояние

Это read-only аудит текущей рабочей копии Era. В ходе аудита не изменялись Kotlin, XML, drawable, Gradle, system UI, Voice Mode, память или OpenAI pipeline.

До аудита в репозитории уже были незакоммиченные изменения пользователя, в том числе в `MainActivity.kt` и voice-файлах. Они сохранены и не относятся к изменениям этого аудита. Новый файл отчёта — единственный файл, созданный в рамках данной задачи.

Основные прочитанные файлы:

- `app/src/main/res/layout/activity_main.xml` — фактическое дерево главного экрана.
- `app/src/main/java/com/era/assistant/MainActivity.kt` — binding, создание message views, scroll, input/send, меню и animation нижней кнопки.
- `app/src/main/AndroidManifest.xml` — `windowSoftInputMode` главной Activity.
- `app/src/main/res/values/styles.xml` и `colors.xml` — тема и system bars.
- `app/src/main/res/drawable/bg_message_input.xml`, `bg_menu_button.xml`, `bg_send_button.xml`, `moon.png`, `earth_send.png` — фоновые ресурсы и нижняя кнопка.
- `app/build.gradle` — `minSdkVersion 23`, `targetSdkVersion 29`, AndroidX Core `1.3.2`.

Voice-файлы (`MicInputUiController`, `VoiceModeController`, `PulseRingAnimator` и связанные STT/TTS классы) просмотрены только настолько, насколько это нужно для определения границ UI. Поведение Voice Mode не является предметом изменения.

## 1. Как сейчас устроен главный экран

Корневой элемент — `FrameLayout` в `activity_main.xml:3-326`. Слои идут сверху вниз так:

1. `eraSpaceBackground` (`ImageView`, `activity_main.xml:11-21`) — `match_parent`, `@drawable/moon`, `centerCrop`, не clickable и исключён из accessibility. Это космический фон внутри окна приложения.
2. `mainContent` (`LinearLayout`, `activity_main.xml:26-195`) — прозрачный вертикальный контейнер с `paddingLeft=12dp`, `paddingTop=8dp`, `paddingRight=12dp`.
3. Внутри `mainContent` находится верхняя панель (`activity_main.xml:38-119`) высотой `48dp`.
4. После неё находится чат `chatScrollView` (`activity_main.xml:124-146`) с `layout_height=0dp` и `layout_weight=1`. Он получает только оставшееся место.
5. После чата находится `inputPanel` (`activity_main.xml:151-193`) высотой `wrap_content`, с `paddingTop=10dp` и `paddingBottom=10dp`.
6. В самом конце корневого `FrameLayout` находятся `menuScrim` и `sideMenu` (`activity_main.xml:198-324`). Это настоящие overlay-слои меню, но они не являются частью обычного chat viewport.

Фактическая верхняя композиция именно такая:

`[menuButton] [noteButton] / Эра / [micButton] [voiceModeButton]`

Кнопки имеют размер `48dp`. Между menu и note, а также между mic и Voice Mode стоят `6dp` margin. Название занимает оставшуюся ширину через `layout_weight=1`. Дизайн круглых кнопок задаётся `bg_menu_button.xml`.

Чат состоит из обычного Android `ScrollView`, внутри которого находится вертикальный `LinearLayout` `chatMessagesContainer`:

- `ScrollView`: `fillViewport=true`, `overScrollMode=never`, `scrollbars=none`, `paddingBottom=8dp`, `layout_marginTop=4dp`.
- `chatMessagesContainer`: `wrap_content`, горизонтальные padding `8dp`, верхний и нижний padding по `12dp`.
- `clipToPadding=false` задан только у `chatScrollView` (`activity_main.xml:130`).
- `clipChildren` в layout явно не задан ни у root, ни у `mainContent`, ни у контейнеров. Отдельного отключения clipping нет.

Message views создаются программно в `MainActivity.kt`:

- `appendUserMessage()` (`602-682`) создаёт `TextView` с фоном `#2A2A2A`, радиусом `20dp`, padding `16dp/11dp`, верхним и нижним margin по `10dp`. `maxWidth` вычисляется как ширина дисплея минус `70dp` (`655-659`).
- `createSphereMessageView()` (`701-747`) создаёт растянутый `TextView` с padding сверху/снизу по `6dp`, margin сверху `8dp` и снизу `10dp`.
- `appendErrorMessage()` (`750-797`) создаёт аналогичный растянутый текст с красным цветом.
- Сообщения selectable (`setTextIsSelectable(true)`).

Input состоит из `messageInput` (`EditText`) и `sendApiButton` (`ImageButton`). Поле имеет `minHeight=52dp`, `maxLines=4`, горизонтальные padding по `18dp`, фон `bg_message_input.xml`. Кнопка send имеет `52dp x 52dp`, margin слева `8dp`, прозрачный фон и `earth_send.png`. В Kotlin её pulse-анимация называется `moonPulse` (`startMoonPulse()`, `animateMoonScale()`), но отдельного `moon`-слоя в input нет.

## 2. Почему текст сейчас резко обрезается

Сейчас это не overlay-композиция. `mainContent` сначала физически расходует место на верхнюю панель, затем на чат, затем на input. Поэтому границы `chatScrollView` находятся:

- снизу верхней панели;
- сверху input panel.

`ScrollView` рисует дочерний контент только в пределах собственного viewport. `clipToPadding=false` позволяет использовать padding как область, через которую содержимое может прокручиваться, но не отменяет границу самого `ScrollView` и не создаёт плавную маску.

В результате, когда bubble или строка сообщения проходит через верхнюю или нижнюю границу `ScrollView`, она исчезает по прямой жёсткой линии. Ни top fade, ни bottom fade, ни alpha-mask сейчас нет. Космический фон виден вокруг прозрачного `mainContent`, но он не участвует в плавном исчезновении текста.

Текущие safe-размеры фактически такие:

- сверху до chat viewport: `8dp` padding root-контейнера + `48dp` верхняя панель + `4dp` margin чата;
- внутри chat content: `12dp` top padding контейнера;
- снизу после chat viewport: input panel с минимумом примерно `52dp` поля/кнопки плюс `10dp + 10dp` vertical padding, а также системная область, которую сейчас резервирует обычное окно.

Автопрокрутка находится в `MainActivity.kt:799-808`:

```kotlin
chatScrollView.post {
    chatScrollView.smoothScrollTo(0, chatMessagesContainer.height)
}
```

Она вызывается после добавления user/assistant/error message и после каждого streaming delta (`1810-1818`). Android сам ограничивает значение максимальным допустимым `scrollY`. Отдельно не вычисляется фактический bottom inset, высота overlay input или размер fade. Поэтому при будущем overlay-переустройстве одной этой формулы будет недостаточно для гарантии читаемой последней строки.

## 3. Отличие от желаемого overlay/edge-to-edge поведения

Желаемая модель предполагает, что conversation layer занимает почти весь экран, а controls и input рисуются поверх него. В текущей модели:

- background действительно лежит нижним слоем, но только внутри обычного окна приложения;
- chat не проходит под top controls;
- chat не проходит под input;
- top controls и input не являются отдельными overlay-слоями;
- fade отсутствует;
- status bar и navigation bar остаются чёрными и не прозрачными;
- system bar и IME insets в коде не читаются.

Таким образом, сейчас есть обычный `vertical LinearLayout`, а не `background -> full conversation -> controls/input overlays`.

## 4. Верхняя область

Верхняя панель сейчас отдельна как XML-блок, но не как overlay: она является первым ребёнком `mainContent` и занимает `48dp` высоты. Сообщения не могут прокручиваться под ней.

Безопасно разрешить сообщениям проходить под controls можно, если:

1. оставить существующие `Button`/`ImageButton` и их drawable без изменения;
2. вынести верхнюю панель в отдельный верхний слой root `FrameLayout`;
3. дать conversation layer верхний scroll padding, не меньший фактической высоты панели плюс небольшой visual gap и длина fade;
4. держать top fade между conversation и controls;
5. сделать fade view некликабельным, чтобы он не перехватывал scroll touch.

При текущей не-edge-to-edge конфигурации status bar уже находится вне content area Activity. Поэтому для локального варианта не нужно трогать status bar: верхняя панель останется под чёрным status bar так же, как сейчас. Нужно учитывать только существующий `paddingTop=8dp`.

Если одновременно включить полноценный edge-to-edge, поведение изменится: content начнёт рисоваться под status bar, и к top controls потребуется добавить `statusBars()`/display-cutout inset. Простого переноса панели без этого будет недостаточно. Существующие круглые кнопки можно сохранить полностью, изменится только контейнер и его inset padding.

## 5. Нижняя область

`inputPanel` сейчас занимает место в `mainContent`; он не перекрывает chat. В overlay-варианте его можно разместить поверх нижней части conversation.

Это безопасно только при динамическом нижнем scroll padding:

`inputPanel measured height + navigation bar inset + fade length + небольшой gap`.

Тогда последний message физически остаётся в scroll content, но при максимальной прокрутке его последняя строка может быть выведена выше input и fade. Простого постоянного `paddingBottom=12dp` недостаточно, потому что высота EditText меняется от одной до четырёх строк, а при IME меняется доступная высота окна.

Нижняя кнопка и её существующая pulse-анимация должны остаться в `inputPanel`; `sendApiButton`, `moonPulse`, `MicInputUiController` и Voice Mode трогать не нужно.

## 6. Как сохранить полностью читаемыми первое и последнее сообщение

Для первого сообщения нужен верхний spacer внутри прокручиваемого content. Он должен быть не просто `12dp`, а учитывать нижнюю границу top controls и top fade. На начальной позиции `scrollY=0` первое сообщение должно начинаться ниже видимой зоны fade.

Для последнего сообщения нужен нижний spacer, вычисляемый после измерения input panel и актуальных system/IME insets. При `scrollToBottom` целевая позиция должна быть фактическим максимальным scroll range после layout, а не только `chatMessagesContainer.height`.

Практическое правило для реализации:

- fade размещать только в пределах safe spacer;
- не делать fade длиннее spacer;
- после изменения высоты input или inset выполнять layout-aware повторную проверку bottom scroll;
- streaming delta прокручивать только если пользователь уже находится около нижнего края, иначе не отнимать у него ручную позицию чтения;
- при автопрокрутке гарантировать, что baseline последней строки находится выше нижнего overlay.

## 7. Варианты fade

### A. Overlay gradient поверх conversation

Два прозрачных `View` поверх верхнего и нижнего края chat viewport. Каждый получает вертикальный gradient и не участвует в touch.

Плюсы:

- минимальное изменение существующей View-архитектуры;
- совместимо с `minSdk 23`, текущим AppCompat и AndroidX;
- хорошо работает с обычными `TextView` bubbles и streaming text;
- легко сохранить существующие кнопки и cosmic background;
- не нужно менять message model или voice pipeline.

Минусы и риски:

- обычный gradient-view визуально накладывается на текст, а не является настоящей alpha-маской sibling `ScrollView`;
- если gradient окрашен в чёрный, он затемняет и content, и участок cosmic background;
- если gradient окрашен в фиксированный цвет, он может заметно отличаться от конкретного участка изображения `moon.png`;
- overlay должен быть некликабельным, иначе он перехватит scroll/selection touch.

Производительность обычно низкая по риску: это два простых градиента, без перерисовки message views. Риск артефактов — средний, главным образом на контрастных bubble и при выборе текста у границы.

### B. Android fading edge

У `ScrollView` есть штатные `fadingEdgeLength`, `requiresFadingEdge` и vertical fading edge API. Это самый короткий эксперимент и совместимо с API 23.

Плюсы:

- не нужен отдельный overlay-контрол;
- не перехватывает touch;
- мало кода и небольшая стоимость отрисовки.

Минусы:

- это старый механизм с ограниченным контролем длины, поведения и визуального цвета;
- он привязан к scroll view, а не к отдельным top/bottom overlay зонам;
- сложнее согласовать его с input overlay, cosmic background и требованием точного safe padding;
- результат зависит от того, как конкретная версия View рисует fading edge и padding.

Для Era это приемлемый диагностический вариант, но не лучший основной контракт дизайна: он решает затухание, но не решает саму перестройку viewport.

### C. Alpha/gradient mask через отдельный UI-компонент

Отдельный `ConversationViewport`/`FadeOverlayView` может рисовать градиент поверх ScrollView. Если нужен именно настоящий fade контента в фон, а не затемняющая вуаль, компонент должен рисовать conversation в layer и применять к нему alpha mask через Canvas/layer compositing.

Плюсы:

- полный контроль над top/bottom зоной и длиной fade;
- можно сделать настоящий alpha transition, через который проявляется cosmic background;
- логика не обязана разрастаться в `MainActivity`.

Минусы и риски:

- заметно сложнее обычного overlay;
- нужно отдельно проверить hardware-accelerated compositing, selection, text rendering и streaming redraw;
- выше риск артефактов на слабых устройствах и при смене IME;
- маска должна оставаться только в conversation layer, иначе можно случайно затронуть controls/input.

Производительность хуже, чем у двух простых gradient views, особенно если каждый кадр streaming текста приводит к обновлению отдельного composited layer. В текущем проекте это следует считать вторым этапом, если простой overlay визуально не даст нужного перехода в cosmic background.

### D. Другой способ: отдельный viewport-контейнер с Canvas fade

Можно сделать небольшой custom `ViewGroup`/`ScrollView` subclass, который управляет scroll padding, draw order и fade в одном месте. Это наиболее контролируемая архитектура для текущего старого View-проекта без перехода на RecyclerView или Compose.

Но это не должно становиться рефакторингом всего чата: текущие `TextView` и способы добавления сообщений можно оставить. Риск и объём выше, чем у A, поэтому это fallback для строгого alpha-mask эффекта.

### Рекомендация по fade

Для первой реализации именно в Era рекомендован вариант **A/C-lite**: отдельный `ConversationViewportController` плюс два простых некликабельных gradient overlay views, при этом safe padding рассчитывается отдельно и всегда больше fade зоны. Это минимальный риск для текущей архитектуры, сохраняет cosmic background и не затрагивает Voice Mode.

Если после визуальной проверки окажется, что «исчезновение в космос» должно быть настоящим, а не мягким затемнением, тот же компонент можно заменить на Canvas alpha-mask. Android fading edge не следует выбирать как основную архитектуру.

## 8. Edge-to-edge readiness

Текущий проект готов к локальному overlay, но не готов к полноценному edge-to-edge без дополнительной работы:

- `MainActivity.onCreate()` (`250-551`) не вызывает `WindowCompat.setDecorFitsSystemWindows(window, false)` и не устанавливает edge-to-edge flags;
- в проекте нет `WindowInsets`, `ViewCompat.setOnApplyWindowInsetsListener`, `WindowInsetsCompat` или IME inset listener;
- `styles.xml:20-25` задаёт чёрные `statusBarColor` и `navigationBarColor`, светлая тема с тёмными status icons отключена;
- `AndroidManifest.xml:25-28` задаёт `adjustResize`;
- `compileSdk/targetSdk=29`, `minSdk=23` (`app/build.gradle:5-10`).

Схема «физический экран -> cosmic background на весь экран -> conversation под system bars -> controls/input overlay» потребовала бы:

1. включить edge-to-edge в Activity/window;
2. сделать system bars прозрачными и проверить контраст системных иконок;
3. применять `systemBars`/display cutout insets к интерактивным controls;
4. отдельно учитывать `ime` inset для input и нижнего scroll padding;
5. проверить Android 6–10, Android 11–14, разные navigation modes и клавиатуру.

Это существенно шире исходной проблемы жёсткой границы chat. Для текущей задачи полноценный edge-to-edge не нужен: достаточно локально растянуть conversation viewport внутри content area Activity, сохранив нынешнюю system UI конфигурацию. Cosmic background при этом уже остаётся видимым за прозрачными слоями приложения.

## 9. Что происходит при открытии клавиатуры

Сейчас `adjustResize` уменьшает доступную высоту окна. В вертикальном `mainContent`:

- верхняя панель остаётся `48dp`;
- weighted `chatScrollView` становится ниже;
- `inputPanel` остаётся внизу уменьшенного content area, над IME;
- `keepInputActive()` (`896-909`) удерживает фокус `messageInput` и ставит курсор в конец;
- `sendApiButton` имеет custom `setOnTouchListener` (`444-479`), который на down/up снова вызывает `keepInputActive()`, поэтому отправка рассчитана на сохранение активного input/IME.

В локальном overlay-варианте `adjustResize` можно сохранить: root content window по-прежнему уменьшится, bottom input overlay окажется над клавиатурой, а conversation viewport станет короче. Но controller должен обновлять bottom padding после фактического измерения input и после resize. Иначе последний message может остаться под input или fade после смены клавиатуры.

В полноценном edge-to-edge одного `adjustResize` недостаточно как архитектурного контракта: потребуется слушать изменения `ime`/system bars insets и синхронно обновлять положение input, fade и scroll range. На Android 10 и ниже особенно важно не полагаться на случайное sibling inset dispatching.

## 10. Scroll, touch и accessibility

Потенциальные проблемы при overlay:

- top/bottom fade views не должны быть `clickable`, `focusable` или важными accessibility nodes;
- только реальные controls должны стоять выше conversation по Z-order и получать touch;
- прозрачные части top/input overlay не должны блокировать drag в ScrollView;
- input overlay должен принимать touch только в своих bounds, а не закрывать весь экран;
- menuScrim уже намеренно перехватывает touch при открытом side menu — это отдельное, ожидаемое поведение.

Существующие кнопки имеют `contentDescription`, но note/mic/voice/send в XML явно имеют `focusable=false`, а send дополнительно полностью потребляет touch в `setOnTouchListener`. Это не проблема fade как такового, но при реализации нельзя ухудшить keyboard/TalkBack navigation. Fade-слои нужно исключить из accessibility, а доступность существующих controls проверить отдельно.

`TextView` messages selectable. Настоящая alpha-маска может визуально уменьшать доступность строк на краю, даже если accessibility node продолжает содержать полный текст. Поэтому safe spacer должен гарантировать, что полное сообщение можно вывести в центральную читаемую область, а fade не должен быть единственным способом «спрятать» boundary.

Автопрокрутка вызывается и при обычном добавлении сообщения, и на каждом streaming delta. После overlay-перестройки нужно не менять voice/OpenAI callbacks, а заменить только расчёт viewport/scroll target. Отдельный риск — пользователь может читать старый текст во время streaming: без проверки близости к bottom текущая политика может продолжать притягивать scroll вниз.

## 11. MainActivity и границы новой логики

Текущий `MainActivity.kt` содержит 2545 строк в рабочей копии. Для UI здесь находятся не только binding, но и:

- `onCreate()` и wiring всех кнопок;
- восстановление conversation;
- построение user/sphere/error message views;
- `scrollChatToBottom()`;
- input focus и send touch;
- side menu;
- нижняя planet/moon pulse-анимация;
- dialogs и значительная часть send/streaming lifecycle.

Рефакторить это в рамках аудита не следует.

При реализации новую логику лучше вынести в отдельный небольшой компонент, например:

`app/src/main/java/com/era/assistant/core/ui/ConversationViewportController.kt`

Его обязанности:

- связать `ScrollView`, `chatMessagesContainer`, top/bottom fade views и input overlay;
- применить system/IME inset значения, если они включены;
- вычислить top/bottom safe padding;
- обновить padding после измерения input;
- дать MainActivity один метод `scrollToLatestMessage()` или аналогичный callback.

Если нужен настоящий Canvas alpha-mask, отдельный `ConversationFadeView.kt`/`ConversationViewportLayout.kt` должен владеть только отрисовкой viewport. `MainActivity` должна получить только минимальный wiring. Voice/STT/TTS, `MicInputUiController`, `VoiceModeController`, `PulseRingAnimator`, память и OpenAI callbacks менять не нужно.

## 12. Файлы, которые пришлось бы изменить при реализации

Для рекомендуемого локального варианта:

1. `app/src/main/res/layout/activity_main.xml` — разделить текущий вертикальный mainContent на conversation layer, top controls overlay и bottom input overlay; добавить IDs для fade/controller; сохранить существующие кнопки и drawable.
2. `app/src/main/java/com/era/assistant/MainActivity.kt` — только binding нового controller и перевод вызовов автопрокрутки на его API. Message creation и Voice Mode не менять.
3. Новый небольшой `ConversationViewportController.kt` и, при необходимости, `ConversationFadeView.kt` — scroll padding, измерение overlay, fade и touch/accessibility policy.

Для полноценного edge-to-edge дополнительно потребовались бы:

4. `MainActivity.kt` — включение window edge-to-edge и подключение inset listener, либо wiring controller.
5. `app/src/main/res/values/styles.xml` (и, возможно, version-qualified style resources) — прозрачность system bars и проверка icon appearance.
6. `AndroidManifest.xml` менять не обязательно, если `adjustResize` сохраняется; его значение нужно проверить на целевых устройствах.

В рамках этого аудита ни один из этих файлов, кроме добавленного отчёта, не изменялся.

## 13. Минимальный рекомендуемый implementation plan

1. Не трогать Voice Mode и не менять system UI: сначала реализовать локальный conversation viewport.
2. Сохранить корневой `FrameLayout` и cosmic `ImageView` первым слоем.
3. Сделать chat ScrollView растянутым слоем под controls/input.
4. Оставить существующие top buttons и inputPanel визуально без изменений, но разместить их отдельными overlay-слоями поверх chat.
5. Добавить два некликабельных fade views между conversation и controls/input.
6. В controller вычислять top safe padding по высоте top controls и bottom safe padding по измеренной высоте input panel, navigation inset и fade length.
7. Исправить только scroll target так, чтобы последняя строка была выше input/fade; после layout/IME resize повторять проверку.
8. Проверить: пустой чат, одно короткое сообщение, длинное первое сообщение, длинное последнее сообщение, streaming response, ручной scroll вверх, смену высоты input до 4 строк, открытие/закрытие IME, TalkBack/keyboard focus и touch по всем четырём верхним кнопкам.
9. Только после этого отдельно решить, нужен ли настоящий Canvas alpha-mask вместо простого gradient overlay.

## Итоговые ответы

1. **Текущий главный экран** — `FrameLayout` с cosmic `ImageView`, прозрачным вертикальным `LinearLayout`, 48dp top bar, weighted `ScrollView` и обычным bottom input panel.
2. **Причина резкого clipping** — chat ограничен прямоугольником `ScrollView`; `clipToPadding=false` не является fade и не отменяет clipping viewport.
3. **Главное отличие** — текущие controls/input занимают layout space, а не лежат поверх full conversation.
4. **Под top controls** — да, безопасно при отдельном overlay-слое и верхнем safe padding; существующие кнопки можно сохранить.
5. **Под input** — да, при динамическом bottom padding по высоте input, system bar и fade.
6. **Читаемость первого/последнего** — гарантируется safe spacers и scroll target после layout, а не одним визуальным fade.
7. **Лучший первый fade** — отдельные top/bottom gradient views в `ConversationViewportController`; для настоящего alpha fade — расширить этот компонент Canvas mask-слоем.
8. **Альтернативы** — штатный Android fading edge, Canvas/custom ViewGroup mask, но fading edge менее контролируем, а настоящий mask сложнее.
9. **Полный edge-to-edge** — для исправления viewport не нужен; рекомендуется сначала локальное overlay-изменение.
10. **Клавиатура** — текущий `adjustResize` уменьшает chat и оставляет input над IME; overlay-варианту потребуется обновление bottom padding после resize/insets.
11. **Риски** — перехват drag overlay-слоями, недоступность крайних строк, автоскролл во время чтения, и уже существующие ограничения `focusable=false`/custom touch у кнопок.
12. **Файлы реализации** — `activity_main.xml`, минимальный wiring в `MainActivity.kt`, новый UI controller/fade component; styles/insets только для отдельного full edge-to-edge этапа.
13. **Что вынести** — весь viewport/fade/inset/scroll-range расчёт в `ConversationViewportController`, не добавляя его в MainActivity.
14. **Минимальный план** — локальный overlay, safe padding, неинтерактивные fades, корректный bottom scroll, затем тесты IME/touch/accessibility.

## RECOMMENDED UI ARCHITECTURE

Рекомендуемая архитектура для текущего проекта — локальный overlay внутри обычного window, без изменения текущей system UI конфигурации:

```text
Root FrameLayout (existing)
├── eraSpaceBackground / cosmic ImageView (existing, full content area)
├── ConversationViewportController-owned layer
│   └── chatScrollView (full available app content area)
│       └── chatMessagesContainer (dynamic top/bottom safe padding)
├── Top fade (non-clickable, visual only)
├── Bottom fade (non-clickable, visual only)
├── Top controls overlay
│   └── existing menu / note / Era / mic / Voice Mode buttons
├── Bottom input overlay
│   └── existing inputPanel / messageInput / sendApiButton
├── menuScrim (existing, only when side menu is open)
└── sideMenu (existing, above all layers when open)
```

В этой схеме conversation может визуально проходить под top controls и input, но его scroll content получает достаточные safe spacers, чтобы первое и последнее сообщение полностью выводились в читаемую область. Fade визуально смягчает границы и не должен становиться препятствием для touch или accessibility.

## Техническое основание

Проверены официальные Android API guidance по `ScrollView` fading edges, `adjustResize` и обработке edge-to-edge/insets:

- [ScrollView API reference](https://developer.android.com/reference/android/widget/ScrollView)
- [Handle input method visibility](https://developer.android.com/develop/ui/views/touch-and-input/keyboard-input/visibility)
- [Manually set up the edge-to-edge display](https://developer.android.com/develop/ui/views/layout/edge-to-edge-manually)
- [Display content edge-to-edge in views](https://developer.android.com/develop/ui/views/layout/edge-to-edge)

