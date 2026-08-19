# Codex Bridge Architecture Audit

Дата: 2026-08-17 UTC.

## 1. Executive verdict

**NEEDS_TERMUX_CONFIGURATION**.

Рабочий технический путь существует: Android-приложение может вызвать
экспортированный `com.termux.app.RunCommandService` через
`com.termux.RUN_COMMAND`, а Termux — запустить фиксированный worker в Debian,
который вызывает `codex exec`. Но текущая установка ещё не готова: в
`~/.termux/termux.properties` параметр `allow-external-apps` закомментирован,
а текущий manifest Эры не содержит `com.termux.permission.RUN_COMMAND`.

Production-код и Termux-конфигурация этим аудитом не изменялись.

## 2. Current environment

Проверено read-only в текущем Android/Termux/proot окружении:

| Component | фактическое состояние |
|---|---|
| Android | 16, API 36 |
| device | TECNO CM5, arm64/aarch64 |
| Termux app | 0.118.3, versionCode 1002 |
| termux-tools | 1.45.0 (`1.46.0+really1.45.0-1` в dpkg metadata) |
| Termux:API | 0.53.0, versionCode 1002 |
| proot-distro | 5.6.0 |
| Debian | 13.6 trixie |
| Codex CLI | `codex-cli 0.147.0` |
| project | `/mnt/sdcard/Era/Era_From_Zip` |
| Era target SDK | 29; compile SDK 29 |

`proot-distro login debian` из уже запущенной Debian/proot-сессии ожидаемо
отказывается от вложенного запуска. Это не ошибка Debian; текущий shell уже
находится в Debian (`/etc/os-release`: Debian 13.6).

Codex использует default `CODEX_HOME` (переменная не задана; в этой сессии это
`/root/.codex`). Без чтения содержимого auth там обнаружены локальные файлы:

```text
thread_history_1.sqlite
state_5.sqlite
logs_2.sqlite
queue_1.sqlite
history.jsonl
```

Это фактическое место локальных thread/session metadata для текущей установки.
Имена и размеры файлов проверены; `auth.json` не читался.

## 3. Best Android → Termux mechanism

### Выбор

Для Era нужен прямой Java/Kotlin `RUN_COMMAND` Intent к Termux, который вызывает
один заранее установленный worker с фиксированным путём. Worker запускает
Debian-команду и Codex. Не следует передавать из Сферы произвольный shell string.

Схема:

```text
Sphere
  → CodexBridgeController
  → explicit Intent: com.termux.RUN_COMMAND
  → Termux RunCommandService
  → fixed Termux worker
  → proot-distro login debian -- fixed Debian worker
  → codex exec --json -C /mnt/sdcard/Era/Era_From_Zip ...
  → bounded result/status
  → Sphere
```

В установленном Termux APK подтверждено: `RunCommandService` имеет
`exported=true`, action `com.termux.RUN_COMMAND` и permission
`com.termux.permission.RUN_COMMAND`. Официальная инструкция Termux требует
также user-granted permission для приложения-отправителя и
`allow-external-apps=true`.[Termux RUN_COMMAND](https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent)

### Сравнение вариантов

| Вариант | Оценка | Причина |
|---|---|---|
| Direct RUN_COMMAND + fixed worker | лучший V0.1 | официальный IPC, низкая latency, exit code/stdout/stderr, без Tasker |
| Termux:Tasker | не нужен | добавляет стороннее приложение и тот же security surface; Tasker не нужен Эре |
| Termux:API | не подходит | установленный plugin даёт Android API из shell, но не является Codex process bridge |
| file queue | fallback | просто и recoverable, но polling/latency и shared-storage race; не подходит для approval/status без дополнительного worker |
| named pipe / Unix socket | V0.2 | хорош для двустороннего потока, но Termux private path недоступен Эре напрямую |
| localhost TCP/HTTP | лучший V0.2 | двусторонний status/output/cancel; нужен token и on-demand worker |
| Codex app-server | V0.2 candidate | stdio/Unix/WS и потенциально richer protocol, но CLI помечает его experimental; approval contract не был подтверждён схемой |
| arbitrary shell via Intent | запрещено | превращает Сферу в unrestricted shell caller |

Прямой RUN_COMMAND callback подходит для финала, но не даёт полноценного
двустороннего протокола. Поэтому итоговая целевая форма — RUN_COMMAND только
как bootstrap on-demand localhost bridge; постоянный daemon сейчас создавать
не нужно.

## 4. Codex invocation contract

Фактический `codex --help` и `codex exec --help` подтверждают:

```text
codex exec [OPTIONS] [PROMPT]
codex exec resume [OPTIONS] [SESSION_ID] [PROMPT]
```

Полезные для Bridge параметры:

- `-C, --cd <DIR>` — workspace root;
- prompt можно передать аргументом или через stdin (`-`);
- `--json` — JSONL events в stdout;
- `-o, --output-last-message <FILE>` — сохранить последнюю модельную реплику;
- `--output-schema <FILE>` — schema для final response;
- `--sandbox read-only|workspace-write|danger-full-access`;
- `--ephemeral` — не сохранять session files; для production bridge его не
  использовать, если нужен resume.

Стартовая команда должна быть построена worker’ом из фиксированных аргументов,
например концептуально:

```text
codex exec --json --color never --sandbox workspace-write \
  -C /mnt/sdcard/Era/Era_From_Zip - < prompt
```

`danger-full-access` и `--dangerously-bypass-approvals-and-sandbox` запрещены.
`--yolo`/эквивалент также не является безопасным bridge default.

Для resume CLI поддерживает:

```text
codex exec resume <EXPLICIT_SESSION_ID> [FOLLOW_UP_PROMPT]
codex exec resume --last [FOLLOW_UP_PROMPT]
```

Bridge должен сохранять конкретный `thread_id` из первого JSONL
`thread.started` и возобновлять только по этому ID. `--last` нельзя применять
автоматически: он может выбрать чужую/не ту последнюю задачу при конкуренции.

## 5. V0.1 architecture

V0.1 ограничивается одной явно авторизованной задачей:

1. Sphere остаётся в `DRAFT_ONLY`, пока пользователь явно не говорит
   «запусти Codex/дай ему работать».
2. `CodexBridgeController` проверяет allowlist workspace, размер prompt и
   отсутствие уже активной задачи.
3. Controller вызывает фиксированный Termux worker через RUN_COMMAND с
   `EXTRA_BACKGROUND=true`, stdin и уникальным task ID.
4. Worker запускает `proot-distro login debian --` и Codex.
5. Worker сохраняет полный stdout/stderr в Termux-private task log, а наружу
   возвращает только bounded result: state, exit code, session ID, tails и
   final message.
6. Android принимает результат через уникальный one-shot `PendingIntent`;
   отдельный result receiver/service не должен писать transcript в logcat.
7. При завершении приложение показывает Sphere итог и список разрешённых report
   files.

RUN_COMMAND официально возвращает для background command отдельные stdout,
stderr и exit code через PendingIntent; для foreground command это объединённый
terminal transcript. Android Intent/result payload ограничен, а Termux
ограничивает возвращаемые stdout/stderr примерно 100 KB combined, поэтому
полный transcript нельзя передавать в Сферу.[RUN_COMMAND result limits](https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent)

Полный status streaming, cancellation и approval relay относятся к V0.2:
worker должен быть on-demand localhost bridge с random bearer token, а не
постоянным сервисом.

## 6. User authorization and security model

Сфера получает не raw shell tool, а узкий API:

```text
draftTask(userRequest) -> DraftTask
startTask(taskId, explicitConfirmation) -> CodexTaskHandle
resumeTask(taskId, explicitConfirmation, continuationPrompt)
getStatus(taskId)
getOutput(taskId, tailOnly=true)
cancelTask(taskId, explicitConfirmation)
```

Состояния авторизации:

- `DRAFT_ONLY`: только сформировать/показать ТЗ;
- `RUN_CODEX`: запускать только после отдельного явного намерения;
- `CONTINUE_CODEX`: продолжать только названную task/session;
- `CANCEL_CODEX`: остановить только названную task.

Одна общая фраза «подумай, как исправить» не является разрешением на Codex.
Каждая новая задача требует нового явного подтверждения. Автоматический
бесконечный resume запрещён.

Allowlist V0.1:

```text
/mnt/sdcard/Era/Era_From_Zip
```

Проверять canonical path после realpath и не принимать `/`, `/data/data`,
`$HOME`, `~`, symlink escape или arbitrary additional directories. Report files
возвращать только как relative paths внутри allowlisted workspace.

Secrets:

- не читать и не передавать Sphere API keys, `auth.json`, `.env`, keystore или
  Codex credentials;
- не помещать secrets в prompt, JSON response или log tail;
- не включать `auth.json`/config credential material в report files;
- использовать существующую Codex auth environment без копирования;
- redact token-like strings перед возвратом результата.

RUN_COMMAND имеет существенный security surface: при предоставленном permission
и включённом `allow-external-apps` приложение может запускать команды в Termux
context. Поэтому Era должна вызывать только hardcoded worker path и не иметь
метода `runShell(anything)`.[Termux privacy warning](https://github.com/termux/termux-tasker#readme)

## 7. Approval model

### V0.1

Не relay’ить approvals автоматически. Если non-interactive Codex ждёт
approval, worker переводит задачу в `NEEDS_USER_ACTION`, сохраняет bounded
описание запроса и останавливается. Сфера спрашивает пользователя; новая
явная команда `CONTINUE_CODEX` продолжает задачу только по сохранённому
session ID.

### V0.2

Approval relay технически вероятен только через двусторонний Codex protocol
(app-server по stdio/Unix/WS) или специально проверенный worker protocol. Локальный
CLI действительно содержит experimental `app-server`, `exec-server` и
`remote-control`, но в этом аудите не подтверждены конкретные request/response
методы approval. Генерация app-server schema была остановлена после отказа
автоматической проверки из-за временного TPM rate limit; повторять вызов не
стало безопаснее. Поэтому статус approval relay сейчас **UNKNOWN**, а не
готовая production capability.

Никогда не использовать blanket auto-approve, `--yolo` или
`--dangerously-bypass-approvals-and-sandbox` от имени Сферы.

## 8. Process lifecycle

Bridge хранит task record и heartbeat отдельно от raw transcript:

```text
CREATED → STARTING → RUNNING → COMPLETED
                         ├── FAILED
                         ├── KILLED
                         └── NEEDS_USER_ACTION
```

Process-level правила:

- `RUNNING`: worker принят Termux и process жив; JSONL `thread.started`/
  `turn.started` подтверждают запуск Codex;
- `COMPLETED`: process exit 0, `turn.completed` и final message получены;
- `FAILED`: non-zero exit, invalid JSONL, startup/auth/config error или
  explicit Codex error;
- `KILLED`: signal/Termux internal error/Android service death без намеренного
  cancel marker;
- `NEEDS_USER_ACTION`: worker остановлен на approval/config/permission
  boundary, а не бесконечно ждёт.

Если приложение отправило cancel, сначала атомарно записать `cancel_requested`
и только затем просить worker завершить child process; иначе различать
намеренный cancel и unexpected Killed нельзя. При потере Termux callback task
остаётся `RUNNING` до heartbeat timeout, затем становится `KILLED_OR_UNKNOWN` и
требует пользовательского решения, а не автоперезапуска.

Resume policy:

- сохранять session ID сразу после `thread.started`;
- после Killed сообщать ID и последний tail;
- не resume автоматически;
- `CONTINUE_CODEX` вызывает `codex exec resume <saved-id> <prompt>`;
- если ID не был получен, не использовать `--last` молча.

## 9. Background execution

Background command должен быть без TTY (`EXTRA_BACKGROUND=true`), иначе Android
может потребовать открыть Termux/увидеть notification для foreground terminal.
Termux documentation отдельно указывает background activity restrictions,
optional Draw Over Apps для terminal session, storage access для shared storage
и battery optimization как возможную причину убийства процесса.

На Android 16 и TECNO/HiOS нет гарантии, что длительный proot/Codex/Gradle
процесс переживёт агрессивное энергосбережение. Минимально необходимы:

- видимое Termux foreground notification для user-visible long task;
- battery-optimization exemption для Termux, если пользователь этого хочет;
- тест при выключенном экране, свёрнутой Эре и свёрнутом Termux;
- heartbeat и recovery state, но не hidden watchdog/autoresume;
- optional partial wake lock только в отдельном обоснованном тесте, не default.

Android foreground services ограничены правилами запуска из background; нельзя
строить решение на скрытом постоянном Android service. Текущий user gesture
«запусти Codex» — правильная точка для запуска visible/background work.
[Android foreground-service guidance](https://developer.android.com/develop/background-work/services/fgs)

## 10. V0.1 API and result format

Минимальный локальный contract:

```json
{
  "taskId": "uuid",
  "sessionId": "uuid-or-null",
  "state": "RUNNING|COMPLETED|FAILED|KILLED|NEEDS_USER_ACTION",
  "startedAt": "RFC3339",
  "finishedAt": "RFC3339-or-null",
  "exitCode": 0,
  "stdoutTail": "bounded",
  "stderrTail": "bounded",
  "finalMessage": "bounded",
  "reportFiles": ["INTERNET_SEARCH_REGRESSION_REPORT.md"],
  "workingDirectory": "/mnt/sdcard/Era/Era_From_Zip"
}
```

Full logs остаются в Termux-private task storage. V0.1 наружный tail должен
иметь жёсткий лимит (например, 8–32 KiB на stream), а `finalMessage` — отдельный
лимит. `reportFiles` не может содержать absolute path или `..`.

V0.2 localhost protocol:

```text
POST /v1/tasks/start
POST /v1/tasks/{id}/resume
GET  /v1/tasks/{id}
GET  /v1/tasks/{id}/events?after=<cursor>
POST /v1/tasks/{id}/cancel
POST /v1/tasks/{id}/approval  # only after protocol is proven
```

Каждый request содержит random bearer token, task ID, action, bounded prompt и
allowlisted workspace. SSE/WebSocket не обязателен: cursor-based polling с
коротким interval проще восстановить после Activity recreation. Loopback
listener должен быть bound to `127.0.0.1` only; не слушать `0.0.0.0`.

## 11. Required Android permissions/configuration

Потребуется отдельный future production change:

```xml
<uses-permission android:name="com.termux.permission.RUN_COMMAND" />
```

И receiver/service внутри Эры для unique one-shot PendingIntent result. Не
делать его exported без необходимости.

Current Era target SDK 29 не требует package visibility policy для target 30+,
но при будущем поднятии target SDK нужно добавить explicit `<queries>` для
`com.termux` вместо широкого `QUERY_ALL_PACKAGES`.

Для shared-storage workspace Termux должен иметь доступ к
`/mnt/sdcard/Era/Era_From_Zip`; Android/Termux storage policy может потребовать
user-granted Files and media / all-files access. Это device setup, не secret
permission.

## 12. Required Termux configuration

На текущем устройстве подтверждено только наличие строки:

```text
# allow-external-apps = true
```

Она закомментирована, поэтому RUN_COMMAND external-app path сейчас не готов.
После отдельного user-approved setup потребуется:

1. включить `allow-external-apps=true` в Termux properties;
2. user-grant Era `Run commands in Termux environment`;
3. проверить Termux storage access к проекту;
4. установить фиксированный worker в заранее определённом Termux/Debian path;
5. проверить battery optimization/notification policy на TECNO;
6. не менять Codex auth или existing session files.

Эти действия намеренно не выполнялись.

## 13. Portability

Portable contract:

- `CodexBridgeController` API;
- JSON task/result schema;
- authorization states;
- workspace allowlist policy as configuration, not hardcoded vendor logic;
- session ID and recovery semantics;
- bounded output and redaction rules.

Device-specific setup:

- Termux installation/source and package versions;
- Debian/proot installation;
- Codex auth;
- Android permission grant and package visibility;
- shared-storage access;
- battery optimization, notification and OEM process policy;
- exact Termux/Debian executable paths.

Не следует зашивать TECNO/HiOS assumptions в bridge protocol.

## 14. Empirical probe

Выполнен один harmless probe без изменения проекта:

```text
codex exec --ephemeral --json --color never --sandbox read-only \
  -C /mnt/sdcard/Era/Era_From_Zip <read-only prompt>
```

Результат: process exit code `0`; stdout содержит JSONL events
`thread.started`, `turn.started`, `item.started`, `item.completed` и
`turn.completed`. Probe не создавал persistent session из-за `--ephemeral`, не
изменял auth/config и не запускал build/network tools. Внутри probe попытки
Git-чтения shared storage получили `182`; это ограничение shell-доступа, а не
Codex invocation failure.

Resume empirically не запускался, поскольку это потребовало бы сохранить и
изменить session state, что запрещено текущим аудитом. Resume подтверждён
локальным CLI help и официальной документацией, но не device end-to-end bridge
probe.

Генерация app-server schema не завершена: автоматическая проверка команды
отклонила её из-за временного TPM rate limit. Это не является доказательством
поломки app-server.

## 15. Risks / blockers

- Current mandatory Termux configuration is absent.
- Current Era manifest has no RUN_COMMAND permission or result receiver.
- RUN_COMMAND grants a powerful cross-app capability; fixed worker and explicit
  user confirmation are mandatory.
- One-shot PendingIntent result is bounded/truncated and not a status stream.
- Long-running proot/Codex may be killed by Termux, Android or OEM policy.
- A killed process may not produce a final callback; heartbeat/recovery is
  required.
- Automatic `--last` resume can select the wrong session.
- Approval relay is not proven for `codex exec --json`; app-server is
  experimental and needs a separate protocol audit.
- Prompt/Intent/result size limits require bounded payloads and local logs.
- Shared storage may be writable by other apps; worker must not store secrets or
  trust arbitrary request files there.

## 16. Implementation plan for a separate future task

### V0.1

1. User-approved Termux setup and a harmless RUN_COMMAND permission probe.
2. Add a small `CodexBridgeController` and private result receiver; keep
   MainActivity wiring minimal.
3. Add fixed worker with exact workspace allowlist and no shell interpolation.
4. Implement one authorized `START`, bounded final result, exit/error mapping and
   task record.
5. Run read-only probe, then one tiny user-approved project task; test screen
   off/background and Termux killed cases.

### V0.2

1. Add on-demand authenticated localhost bridge for status/events/output/cancel.
2. Add explicit session resume with stored ID and no automatic retry.
3. Audit app-server protocol and implement approval relay only if its
   request/response contract is stable and testable.
4. Add report-file discovery, redaction tests, cursor recovery and migration
   handling.

No architecture-wide refactor, permanent daemon, auth migration, or changes to
Internet Search/Voice/Memory/RAW/Usage/OpenAI/xAI are required.

## Final terminal output

```text
VERDICT: NEEDS_TERMUX_CONFIGURATION
BEST BRIDGE: Android RUN_COMMAND Intent -> fixed Termux/Debian worker -> codex exec --json; localhost authenticated bridge for V0.2
CODEX NONINTERACTIVE: YES
RESUME SUPPORTED: YES
STATUS AVAILABLE: PARTIAL
CANCEL AVAILABLE: YES (worker/bridge design; not a direct RUN_COMMAND primitive)
APPROVAL RELAY POSSIBLE: UNKNOWN
ANDROID CONFIG REQUIRED: RUN_COMMAND permission, result receiver/PendingIntent, future package visibility if targetSdk >= 30, shared-storage access
TERMUX CONFIG REQUIRED: allow-external-apps=true, user grant for Era, fixed worker, storage access, battery/notification setup
V0.1 COMPLEXITY: MEDIUM
REPORT: CODEX_BRIDGE_ARCHITECTURE_AUDIT.md
PRODUCTION CODE CHANGED: NO
COMMIT/PUSH: NOT DONE
```
