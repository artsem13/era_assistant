# Internet Search Regression Report

Дата расследования: 2026-08-17 UTC.

## Ограничение доказательной базы

Актуальная Android app-private база и app-private xAI raw archive из текущего
Termux/Debian окружения недоступны. В проекте и в доступной части
`/mnt/sdcard` не найден файл `era_conversation_archive.db`; старую или
переносимую копию молча использовать нельзя. Поэтому реальные пользовательские
turn’ы Артёма в этом расследовании не анализировались, а точный failure point
конкретного turn’а не может быть установлен честно.

Для полного расследования нужен read-only экспорт актуальной базы с устройства
или диагностический запуск приложения, который передаст копию рабочей базы и
`filesDir/xai_search_raw` без изменения оригиналов и без API-запросов.

## Storage и source of truth

`ConversationArchive` — `SQLiteOpenHelper` с именем
`era_conversation_archive.db`. Android фактический рабочий путь строится как:

```text
context.getDatabasePath("era_conversation_archive.db")
```

Для пакета `com.era.assistant` это app-private database storage (обычно
`/data/user/0/com.era.assistant/databases/era_conversation_archive.db`;
точный device path должен быть подтверждён логом устройства).

Актуальный source of truth — рабочая база из `getDatabasePath`, не backup.
После записи сообщения `LocalMemoryBackup` асинхронно делает WAL checkpoint и
копирует базу в MediaStore relative path:

```text
Download/Era/memory/raw/era_conversation_archive.db
```

Копирование не даёт в текущем коде проверяемого version/timestamp manifest, и
доступная среда не содержит ни рабочей базы, ни подтверждённой копии. Состояние
backup относительно рабочей базы: UNKNOWN.

Conversation RAW содержит только `messages` и `research_notes`. Search raw не
пишется в эту SQLite-базу.

## Реальные сообщения

Реальные сообщения: **не доступны для анализа**. Поэтому таблица требуемых
turn’ов имеет следующий фактический результат:

| USER MESSAGE | SEARCH DECISION | XAI CALLED | XAI RESPONSE | EVIDENCE | OPENAI | FINAL RESULT | FAILURE POINT |
|---|---|---|---|---|---|---|---|
| — | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | unavailable RAW/device logs |

Фразы из задания не считаются реальными сообщениями пользователя. Их можно
проверить только как offline routing examples.

## Что доказано по routing

`SearchOrchestrator.run()` вызывает `SearchDecisionController` до OpenAI. При
`NO_SEARCH` он немедленно вызывает `onSuccess(null)`, а OpenAI получает только
обычные инструкции. Следовательно, пользовательская инструкция сферы про
самостоятельный интернет-поиск не может запустить deterministic router.

Текущая эвристика использует `toLowerCase().trim()` и `contains`, поэтому
пунктуация и регистр не мешают распознаванию. Она распознаёт, например:

- `Посмотри погоду в Красноярске` → `GENERAL_WEB` (`погода`);
- `что сейчас происходит` → `GENERAL_WEB` (`сейчас`);
- `какая сегодня погода` → `GENERAL_WEB`;
- `какие последние новости` → `GENERAL_WEB`;
- `сколько сейчас стоит ...` → `GENERAL_WEB`.

Но она пропускает актуальные формулировки без перечисленных stem’ов, например
`посмотри прогноз в Красноярске`, `температура в Красноярске`, `доступен ли
сервис сейчас` если в запросе нет `сейчас`, а также часть вопросов о текущем
состоянии сервисов и событий. Это подтверждённый routing coverage gap, но не
доказательство того, что именно он сломал конкретный turn без RAW.

## Pipeline inspection

Статический путь такой:

```text
sendTextToSphere
  → memory retrieval
  → SearchOrchestrator.run
  → SearchDecisionController
  → XaiSearchClient.search
  → SearchRawArchive.save
  → XaiSearchResponseParser.parse
  → EvidenceBundle.toOpenAiContext
  → existing OpenAI streaming pipeline
  → UI / ConversationArchive assistant message
```

Если decision не `NO_SEARCH`, wiring передаёт `EvidenceBundle` в OpenAI
instructions и записывает usage после успешного parse. Отдельных production
логов decision, XAI request/result или evidence в ConversationArchive нет; по
одной SQLite messages-базе восстановить всю цепочку нельзя.

Не найдено статического доказательства, что parser теряет наблюдаемые fixture
формы: offline fixtures 01–04 уже использовались в предыдущем отчёте, включая
web sources, citations, custom X tool calls и usage. Не доказаны реальные
lifecycle/UI ошибки для пользовательских turn’ов.

## Search raw archive и usage

Успешный xAI response сохраняется до parser в:

```text
context.filesDir/xai_search_raw/search_<epoch>_<mode>.json
```

Записываются conversation id, message id, query, mode, request, response,
timestamps и latency; API key и Authorization header не сохраняются. Этот
каталог app-private и не копируется `LocalMemoryBackup` в portable RAW.

`SearchUsageTracker` на успешном `EvidenceBundle` увеличивает request count,
input/cached/output/reasoning/total tokens, web/X tool calls, cost ticks и
сохраняет последнюю latency. `UsageActivity` читает эти SharedPreferences и
показывает xAI страницу. Для неуспешного HTTP/parse запроса usage не
записывается. Фактические production counters и raw files без app-private
доступа: UNKNOWN.

## Причина регрессии и минимальный fix

Однозначная причина production regression **не установлена**, потому что
отсутствует актуальный RAW/device trace. Наиболее сильный статически
подтверждённый кандидат — слишком узкое deterministic routing coverage; сама
фраза про интернет в user instructions это не исправляет.

Минимальный потенциальный fix после получения реальных failing messages —
расширить `SearchDecisionController` небольшими проверяемыми русскими stems для
прогноза/температуры/доступности сервисов и текущих событий, затем прогнать
offline matrix. Архитектура и `MainActivity` для этого не требуют изменения.
Нельзя выбирать точный список или менять production code до подтверждения
реальными сообщениями и решениями.

Production-файл, вероятно, потребуется изменить: `core/search/SearchDecisionController.kt`.
Архитектурный рефакторинг не нужен. Voice, Memory и RAW archive не менялись.

Платные probes: **NO**. Использованы код, текущий diff, существующий report и
offline fixtures.

Build: **NOT RUN** — это documentation-only diagnostic pass; предыдущий report
фиксирует известное убийство Gradle daemon на `mergeDebugResources` без
compiler/resource ошибки. APK для текущего состояния не заявляется.

## VERDICT

```text
VERDICT: Diagnosis incomplete until read-only device data is supplied
ROOT_CAUSE: Not proven; deterministic routing coverage gap is the leading static candidate
RAW_SOURCE_OF_TRUTH: app-private getDatabasePath("era_conversation_archive.db")
REAL_MESSAGES_ANALYZED: 0 (unavailable from current environment)
SEARCH_ROUTING: deterministic pre-OpenAI; known coverage gaps
XAI_CLIENT: statically wired; real production result unknown
PARSER: offline fixture contract passes per existing report; production result unknown
EVIDENCE_PIPELINE: statically wired into OpenAI instructions
RAW_SEARCH_ARCHIVE: app-private filesDir/xai_search_raw; not portable-RAW backed up
XAI_USAGE: statically tracked on successful evidence; production counters unknown
CODE_CHANGE_REQUIRED: NO (until real failure is proven)
FILES_TO_CHANGE: none now; likely SearchDecisionController.kt after evidence
PAID_PROBES_PERFORMED: NO
BUILD_STATUS: NOT RUN (documentation-only)
NEXT_ACTION: obtain read-only copies/logs from the active device, then correlate messages by conversation_id/message_id/time
```
