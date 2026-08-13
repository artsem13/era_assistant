# Инструкции Codex для проекта «Эра»

## Область работы

- Канонический репозиторий: `/mnt/sdcard/Era/Era_From_Zip`. Не искать и не использовать другие копии проекта.
- Перед началом определить минимальную область задачи и сначала читать 1–5 наиболее вероятных файлов. Не проводить полный аудит без необходимости.
- Не повторять одинаковые чтения, поиск, `git status` или `git diff`, если состояние не могло измениться.
- Не оптимизировать и не рефакторить код вне текущей задачи.
- Обычно: изменения → минимальная проверка → одна сборка в конце. Не запускать `clean` без причины.
- При неоднозначности, способной привести к большим изменениям, остановиться и сообщить пользователю.
- Не менять Gradle, Android Gradle Plugin, Kotlin, SDK, Java, зависимости или системные пакеты без прямого разрешения.

Если `apply_patch` на Android shared storage возвращает известную файловую ошибку/status 182, не повторять его многократно: после одной подтверждённой ошибки использовать безопасный точечный fallback, менять только разрешённые файлы и проверять diff.

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

APK после успешной сборки: `app/build/outputs/apk/debug/app-debug.apk`. Строка `BUILD SUCCESSFUL` завершает сборочную диагностику; обычные compiler/Gradle warnings не являются ошибкой. `--stacktrace`, `--info` и `--debug` использовать только после реального `BUILD FAILED`, если обычного сообщения недостаточно.

### Экономная проверка сборки

- Не запускать повторно долгие диагностические исследования Gradle/AAPT2, если проблема окружения уже описана в этом файле.
- Сборка из sandbox-среды Codex может завершаться ошибкой `Gradle build daemon disappeared unexpectedly`, даже когда проект исправен и успешно собирается вручную в рабочем Debian/Termux окружении.
- Если такая ошибка уже идентифицирована как ограничение sandbox, не исследовать её заново и не читать большие Gradle-логи без явной необходимости. Подготовить изменения и попросить пользователя выполнить `bash tools/build-debug.sh` в обычном рабочем терминале.
- Для продолжения достаточно результата `BUILD SUCCESSFUL` либо первой существенной ошибки сборки.
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

Финальный отчёт держать коротким: изменённые файлы, минимальная проверка, сборка и известные ограничения.
