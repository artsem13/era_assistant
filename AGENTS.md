# Инструкции Codex для проекта «Эра»

## Область работы

- Канонический репозиторий: `/mnt/sdcard/Era/Era_From_Zip`. Не искать и не использовать другие копии проекта.
- Перед началом определить минимальную область задачи и сначала читать 1–5 наиболее вероятных файлов. Не проводить полный аудит без необходимости.
- Не повторять одинаковые чтения, поиск, `git status` или `git diff`, если состояние не могло измениться.
- Не оптимизировать и не рефакторить код вне текущей задачи.
- Обычно: изменения → минимальная проверка → одна сборка в конце. Не запускать `clean` без причины.
- При неоднозначности, способной привести к большим изменениям, остановиться и сообщить пользователю.
- Не менять Gradle, Android Gradle Plugin, Kotlin, SDK, Java, зависимости или системные пакеты без прямого разрешения.

Если apply_patch на Android shared storage возвращает status 182, не повторять его многократно. При fallback через /tmp копировать туда только разрешённые файлы, выполнять все промежуточные edits над /tmp-копиями, проверять их там, сравнить original ↔ edited и вернуть каждый подтверждённый файл обратно один раз.

## Архитектура

- `MainActivity.kt` — минимальная точка связывания UI с контроллерами; не раздувать её. Новую бизнес-логику, сеть, хранение, состояния, память, voice/STT/TTS, Usage, dialogs, parsing и integrations выносить в небольшие отдельные Kotlin-классы.
- Если `MainActivity.kt` неизбежно меняется, добавлять только минимальное wiring; реализацию функции туда не переносить. В финальном отчёте указывать изменение количества её строк.
- Не объединять независимые обязанности в один большой класс и не проводить сторонний рефакторинг.
- Код приложения и пользовательские данные разделять; компоненты проектировать переносимыми между устройствами независимо от APK.
- Не менять существующие рабочие зоны памяти, RAW archive, Research Notes, Usage, STT, OpenAI streaming и TTS при работе над другой зоной без необходимости.
- Не хардкодить и не коммитить API-ключи, секреты, keystore/private keys и персональные данные. `local.properties` — машинно-зависимая конфигурация среды, не конфигурация приложения.

## Каноническая среда сборки

- Сборка выполняется непосредственно на ARM64/aarch64 Android через Termux + proot Debian; AndroidIDE не является каноническим окружением.
- В Debian: `proot-distro login debian`; `JAVA_HOME=/opt/java11`; рабочая Java — Temurin/OpenJDK 11.
- Gradle wrapper: 6.1.1; Android Gradle Plugin: 3.5.3; `compileSdk`, `targetSdk` и платформа: 29.
- SDK: `/opt/android-sdk/android-sdk`; `local.properties` должен содержать `sdk.dir=/opt/android-sdk/android-sdk`.
- `gradlew` на shared storage может иметь mode 660 — это нормально. Запускать через `bash ./gradlew ...`, не исправлять `chmod`.

### Обязательный AAPT2 override

Не использовать desktop Maven AAPT2 или AndroidIDE AAPT2 `/opt/android-sdk/android-sdk/build-tools/33.0.3/aapt2` как Debian/glibc binary. Ошибка `/lib/aarch64-linux-gnu/libm.so: invalid ELF header` означает конфликт Android binary с GNU linker scripts, а не повреждение Debian `libm.so`/`libc.so`.

Рабочий AAPT2 находится в Termux: `/data/data/com.termux/files/usr/bin/aapt2`. В `gradle.properties` должен сохраняться:

```properties
android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2
```

При `AAPT2 Daemon startup failed` сначала проверить этот override. Не заменять linker scripts, не создавать symlink в `/lib` и не переустанавливать glibc/SDK/JDK/Gradle без доказанной причины.

## Рабочий маршрут сборки

Перед сборкой:

```sh
export JAVA_HOME=/opt/java11
export ANDROID_HOME=/opt/android-sdk/android-sdk
export ANDROID_SDK_ROOT=/opt/android-sdk/android-sdk
export PATH="$JAVA_HOME/bin:$PATH"
```

Канонический способ сборки проекта из корня репозитория — `bash tools/build-debug.sh`. Обёртка не устанавливает пакеты, не меняет системную конфигурацию и не выполняет `clean`. Прямой запуск `bash ./gradlew assembleDebug` использовать только если это явно необходимо для диагностики.

APK после успешной сборки: app/build/outputs/apk/debug/app-debug.apk. BUILD SUCCESSFUL означает только успешную сборку проекта; это не доказательство корректности scope, поведения приложения или human/device test.

### Экономная проверка сборки

- Не запускать повторно долгие диагностические исследования Gradle/AAPT2, если проблема окружения уже описана в этом файле.
- Сборка из sandbox-среды Codex может завершаться ошибкой `Gradle build daemon disappeared unexpectedly`, даже когда проект исправен и успешно собирается вручную в рабочем Debian/Termux окружении.
- Если такая ошибка уже идентифицирована как ограничение sandbox, не исследовать её заново и не читать большие Gradle-логи без явной необходимости. Подготовить изменения и попросить пользователя выполнить `bash tools/build-debug.sh` в обычном рабочем терминале.
- Для продолжения сборочной диагностики достаточно результата BUILD SUCCESSFUL либо первой существенной ошибки; scope и функциональную корректность проверять отдельно.
- Экономить токены: не перечитывать без необходимости весь репозиторий, длинные логи и уже проверенные файлы. Сначала использовать `git status`, `git diff --stat`, затем читать только файлы, относящиеся к текущей задаче.
- Не повторять проверки, результат которых уже получен в текущей сессии и остаётся актуальным.

## Диагностика по слоям

Сначала определить слой и читать только относящиеся к нему файлы:

- Kotlin/Java → исходный файл и связанные interfaces/classes.
- Resources/AAPT2 → текущий AAPT2 override и первая существенная resource error.
- Gradle/configuration → первая существенная ошибка конфигурации или dependency.
- APK создан → не трогать систему сборки без доказательства проблемы APK.
- APK валиден, но не устанавливается → Package Installer, signature, Play Protect и другие install/security layers, а не исходный код.

Не перескакивать между слоями и не исследовать всю систему из-за одной ошибки. Использовать первую существенную ошибку, а не вторичный stack trace.

Для APK при необходимости проверять: `aapt dump badging <apk>`, `unzip -t <apk>`, `apksigner verify --verbose --print-certs <apk>`. Для пакета `com.era.assistant` с min/target SDK 23/29 блокировка sideload со стороны Google Play Protect — install-layer issue, не build failure. Не отключать Play Protect и не менять безопасность устройства автоматически.

## Git и установка

- Для локальных/read-only Git-команд на shared storage использовать `git -c safe.directory=/mnt/sdcard/Era/Era_From_Zip ...`; не повторять диагностику `dubious ownership` и не менять global Git config без необходимости.
- Без прямого разрешения пользователя не выполнять `commit`, `push`, `reset`, `checkout`, `restore` или `clean`. Push — только после подтверждения протестированной версии.
- Не предлагать по умолчанию `adb install`, wireless debugging или `cmd package install` из Termux: они не являются рабочей основой этого build loop и могут упираться в Android permissions/SELinux/PackageInstaller.

## Постоянные инструкции

Если пользователь и Codex подтвердили новый устойчивый build route, архитектурную границу или системную особенность, предложить добавить факт в этот файл. Не добавлять временные ошибки, разовые эксперименты и непроверенные гипотезы.

Финальный отчёт держать коротким: задача, изменённые tracked files, subsystem passports updated / not applicable, scope/diff, checks, build result/APK если применимо, human test, migration impact и ограничения.


## Подсистемный контекст и инженерная память

Будущая компактная инженерная память проекта размещается в docs/agent-context/. Паспорта создаются только по фактической архитектуре проекта; возможные примеры: VOICE.md, UI.md, MEMORY.md, API.md, INTERNET.md, IMAGE_GENERATION.md, USAGE.md, DEVICE_BRIDGE.md. В рамках отдельной задачи не создавать паспортные файлы без прямого scope.

Перед любой задачей:

1. Полностью прочитать AGENTS.md.
2. Определить затрагиваемую подсистему или подсистемы.
3. Если для них существуют соответствующие docs/agent-context/*.md, полностью прочитать их до изменения кода.
4. Проверить git status --short.
5. Сопоставить паспорт с фактическим кодом.
6. При противоречии считать код и Git текущей истиной, отметить паспорт как устаревший и после успешной задачи синхронизировать его.
7. Только после этого начинать изменения.

После успешной задачи:

1. Проверить scope и diff.
2. Выполнить необходимые checks/build.
3. Только после подтверждения нового состояния обновить паспорт затронутой подсистемы.
4. Записать в паспорт финальное фактическое состояние, а не дневник действий агента.

Каждый паспорт должен разделять минимум:

- Current State — архитектура, классы, data flow, параметры, interfaces и важные зависимости.
- Known Traps / Lessons — только подтверждённые ошибки и устойчивые грабли с условиями, при которых их нельзя повторять.
- Required Verification — минимальные проверки для изменений подсистемы.

Не записывать в паспорта временные гипотезы, случайные compile errors, неподтверждённые предположения или полный transcript работы агента.

## Источник истины и жёсткая граница scope

- Код и Git — источник истины о текущей реализации.
- docs/agent-context/*.md — компактная инженерная память, объясняющая текущее устройство и подтверждённые решения.
- AGENTS.md — постоянный протокол работы агента.

Перед первым изменением зафиксировать git status --short. Если задача разрешает менять конкретные файлы или подсистему, это жёсткая граница. После каждого логического edit-pass по возможности проверять git status --short и git diff --stat. Перед build обязательно выполнить git status --short, git diff --stat и git diff --check.

Если появился неожиданный tracked file или diff значительно больше ожидаемого, остановиться, выяснить причину и не продолжать build или объявлять задачу завершённой. BUILD SUCCESSFUL не отменяет неправильный scope. В финальном отчёте не писать «изменены только N файлов», если это не подтверждено Git.

## Безопасное редактирование

Запрещено использовать сложные multiline perl/sed regex для структурных изменений Kotlin/Java/XML, особенно при изменении методов, классов, callbacks, lifecycle blocks, вложенных braces или больших XML-блоков.

Предпочтительный порядок: нормальный patch/edit mechanism; небольшой deterministic Python script; либо создание полной временной версии файла в /tmp с проверкой и одной заменой оригинала. Если способ редактирования дал syntax/regex error, не повторять серию вариантов того же хрупкого подхода — сменить стратегию.

## Экономный рабочий принцип

Read once, reason once, edit deliberately.

- Сначала читать минимально необходимый набор файлов и составлять короткую фактическую карту проблемы.
- Не перечитывать неизменившийся файл целиком и не повторять одинаковые rg/search/wc/Git-команды без изменения состояния или новой причины.
- Предпочитать один продуманный edit-pass серии мелких regex-мутаций.
- Не запускать build после каждой мелкой правки; обычно выполнять один build после завершённого логического code-changing pass.
- Если build упал из-за текущих изменений, исправить причину и повторить build.
- Если изменялась только документация, build не нужен.
- Human/device tests отделять от compile/build verification.

## Переносимость между Android-устройствами

Эру проектировать так, чтобы переход на другой Android-телефон не требовал переписывания подсистем и не приводил к потере переносимого состояния. Разделять код приложения, переносимые данные Эры и device-specific Android state.

К переносимым данным относятся по мере появления RAW archive, structured memory, instructions, Research Notes, user settings, Usage/local persistent state и другие долгоживущие данные Эры. К device-specific относятся Android permissions, battery optimization, конкретные Bluetooth routes/devices, hardware identifiers, sensor calibration, notification/system permissions, filesystem/device peculiarities, pairing и системные настройки телефона.

- Git остаётся source of truth для кода.
- Не хардкодить TECNO-specific assumptions в общей архитектуре.
- Предпочитать стандартные Android API, а vendor-specific поведение изолировать behind adapter/provider.
- Обеспечивать safe fallback и schema/version-friendly persisted data formats.
- Не реализовывать Export/Import без отдельного ТЗ, но каждую новую подсистему проектировать готовой к будущей миграции.

При изменениях storage/settings/memory/sensors/audio/device integration оценивать Migration impact: none, portable, device-specific или future migration handling required.

## Подтверждённые уроки подсистем

Если задача подтверждает устойчивую техническую причину, которая с высокой вероятностью повторится, после успешной проверки добавить её в Known Traps / Lessons соответствующего subsystem passport. Не добавлять каждый эксперимент в AGENTS.md; этот файл остаётся короткой конституцией процесса, а детали подсистем живут в docs/agent-context/.

## Постоянное правило Voice / Audio / STT

Диагностику Voice/Audio/STT проводить в порядке:

route → AudioRecord/capture configuration → PCM signal → STT transport → transcript → Smart Turn/endpointing → text post-processing

Не компенсировать повреждённый audio input transcript-фильтрами, blacklist/whitelist, удалением th/what, искусственным объединением transcripts, переписыванием языка или tuning thresholds, пока не доказана корректность capture/PCM. Текущие конкретные параметры Voice хранить в VOICE.md, когда паспорт будет создан, а не раздувать ими AGENTS.md.

## Ответственность за build и финальный отчёт

Codex самостоятельно выполняет canonical build после code-changing pass, чтобы поймать compile/resource errors. Для documentation-only pass build не выполняется. После последнего изменения кода финальный APK должен быть собран именно из последнего состояния файлов.

Финальный отчёт должен быть коротким и фактическим: задача; изменённые tracked files; обновлённые паспорта или not applicable; проверка diff scope; checks; build result и APK path если применимо; обязательный human test если применимо; migration impact; известные ограничения.
