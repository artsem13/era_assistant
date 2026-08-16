Да. Сейчас как раз стоит сделать не красивое резюме, а технический паспорт-снимок, чтобы через два дня мы могли открыть его вместе с заметками и буквально восстановить трассу каждого косяка.
Ниже я фиксирую только то, что следует из нашего текущего кода и тестов. Где состояние требует проверки перед коммитом, я это отдельно отмечаю.
ЭРА / ПАМЯТЬ СФЕРЫ
Технический паспорт экспериментальной долгосрочной памяти v0.2
Дата: 12.08.2026
Статус: собран первый полный экспериментальный цикл долгосрочной памяти. Проект успешно компилируется. Следующий этап: 1–2 дня реального использования Эры с фиксацией ошибок через заметки.
1. Цель текущей версии
Текущая память Эры предназначена именно для долговременного контекста, который должен переживать текущий разговор и не зависеть от контекста, удерживаемого OpenAI.
Отдельную собственную кратковременную память сейчас не строим.
Разделение ответственности:
OpenAI Responses API
    ↓
контекст текущего разговора

Эра
    ↓
собственная долговременная память
Долговременная память принадлежит Эре и хранится локально в SQLite.
2. Полный рабочий тракт
На момент этого паспорта архитектура выглядит так:
ПОЛЬЗОВАТЕЛЬ
    │
    ▼
сообщение
    │
    ├─────────────────────────────────────────────┐
    │                                             │
    ▼                                             │
RAW SQLite                                        │
сообщение сохраняется                             │
    │                                             │
    │                                             ▼
    │                                  MEMORY TOPIC ROUTER
    │                                      GPT-5 mini
    │                                             │
    │                                  выбирает 0–3 topic
    │                                             │
    │                                             ▼
    │                                     memory_topics
    │                                             │
    │                                             ▼
    │                              memory_items выбранных topic
    │                                             │
    │                                             ▼
    │                                  релевантный memory context
    │                                             │
    │                                             ▼
    │                                      OpenAIClient
    │                                             │
    │                                             ▼
    │                                      основная модель
    │                                             │
    │                                             ▼
    │                                         ответ
    │                                             │
    └─────────────────────────────── ответ сохраняется в RAW
                                                  │
                                                  ▼
                                         RawBlockCoordinator
                                                  │
                                                  ▼
                                             RAW Block
                                             ~целевой 4000
                                                  │
                                                  ▼
                                           Memory Compiler
                                             GPT-5 mini
                                                  │
                                                  ▼
                                         0...N memory atoms
                                                  │
                                                  ▼
                                      topic + content + source
                                                  │
                                                  ▼
                                            memory_items
Таким образом память уже имеет замкнутый цикл записи и чтения.
3. RAW: первичный архив
Каждое сообщение пользователя и каждый ответ Сферы сохраняются в SQLite.
Основная таблица:
messages
Минимально важные поля:
id
conversation_id
role
text
role содержит как минимум:
user
assistant
RAW является первоисточником.
Memory Compiler не должен изменять RAW.
Если долговременная память окажется неправильной, мы должны иметь возможность пройти назад до исходных сообщений.
4. RAW Blocks
Компонент:
core/memory/RawBlockManager.kt
Таблица:
raw_blocks
RAW Block не копирует текст сообщений.
Он хранит диапазон исходного RAW:
start_message_id
end_message_id
и служебные данные:
id
conversation_id
start_message_id
end_message_id
estimated_tokens
status
created_at
processed_at
Статусы:
ready
processed
Фактический текст блока при необходимости восстанавливается через messages.
5. Размер RAW Block
Исторически для ускоренного лабораторного теста использовалось:
RAW_BLOCK_TARGET_TOKENS = 500
Целевое значение принято:
RAW_BLOCK_TARGET_TOKENS = 4000
ВАЖНО: непосредственно перед созданием этого паспорта в коде ещё стояло 500. Было принято решение вернуть 4000, но в разговоре после этого нет отдельного подтверждения «поменял».
Поэтому перед GitHub-коммитом обязательно проверить RawBlockManager.kt.
Нужное состояние:
const val RAW_BLOCK_TARGET_TOKENS =
    4000
Это одна из первых вещей, которые проверяем при следующем анализе.
6. Оценка количества токенов
Пока используется не настоящий токенизатор.
Оценка приблизительная:
chars / 4
с минимумом в один токен для непустого текста.
Следовательно:
estimated_tokens ≠ реальные API tokens
Это сознательное упрощение v0.1.
Для текущего эксперимента точная токенизация не считается необходимой.
7. Закрытие RAW Block
Блок не закрывается произвольно при достижении порога.
После достижения порога система ждёт сообщение:
role = assistant
и завершает блок после ответа Сферы.
То есть пользовательская реплика не должна оставаться на границе блока без соответствующего ответа.
8. RawBlockCoordinator
Компонент:
core/memory/RawBlockCoordinator.kt
Он вызывается после успешного сохранения ответа assistant из MainActivity.
Текущая последовательность:
assistant saved
    ↓
RawBlockCoordinator.onAssistantMessageSaved()
    ↓
RawBlockManager.tryCreateNextBlock()
    ↓
если блок готов
    ↓
RawBlockFormatter
    ↓
MemoryCompiler
Coordinator также использует:
MemoryCompilerRunStore
MemoryItemStore
LocalMemoryBackup
Перед запуском Compiler он получает:
memoryItemStore.getTopics()
То есть Compiler уже видит существующую карту смысловой памяти.
9. RawBlockFormatter
Компонент:
core/memory/RawBlockFormatter.kt
Его задача:
List<ArchivedMessage>
    ↓
текстовый RAW Block для GPT-5 mini
Критически важно, чтобы формат содержал исходные MESSAGE_ID, потому что Memory Compiler обязан возвращать source_message_ids.
10. Memory Compiler
Компонент:
core/memory/MemoryCompiler.kt
Модель:
gpt-5-mini
Endpoint:
POST /v1/responses
Его задача теперь не summary.
Текущая задача:
RAW Block
+
карта существующих memory_topics
    ↓
0...N атомарных кандидатов долговременной памяти
Нормальный результат:
{
  "memories": []
}
То есть Compiler имеет право решить:
в этом RAW Block нечего сохранять
11. Что Compiler должен сохранять
По текущему промпту потенциальными кандидатами являются устойчивые сведения и предпочтения пользователя, долговременные инструкции, устойчивые прямо сообщённые факты, принятые решения, состояние длительных проектов, планы, переживающие текущий разговор, незавершённые задачи и обновления ранее известной информации.
Compiler должен быть консервативным.
Пример:
пользователь один раз спросил о Марсе
не должен превращаться в:
пользователь интересуется колонизацией Марса
12. Что Compiler не должен сохранять
Не должны автоматически становиться долговременной памятью обычное содержание разговора, пересказ ответов Сферы, действия ассистента, одноразовые требования, временный формат ответа, тестовые задания, случайные вопросы и неподтверждённые выводы о пользователе.
Один кандидат должен содержать:
один самостоятельный смысл
13. Формат ответа Memory Compiler
Текущий ожидаемый JSON:
{
  "memories": [
    {
      "content": "Один самостоятельный смысл.",
      "topic": "Название смыслового блока",
      "topic_description": "Короткое описание смысловой области.",
      "source_message_ids": [123]
    }
  ]
}
Если сохранять нечего:
{
  "memories": []
}
Compiler не должен добавлять Markdown или произвольные дополнительные поля.
14. Смысловые блоки памяти
Новая концепция v0.1:
memory_topics
    ↓
крупные смысловые коробки

memory_items
    ↓
атомарные воспоминания внутри коробок
Категории не прописываются вручную.
Их создаёт GPT-5 mini по смыслу.
Примеры возможных topic:
Пользователь
Эра / разработка
Здоровье
Работа
Техника
Планы
Предпочтения общения
Это примеры, а не заранее заданный список.
15. Правило создания topic
Memory Compiler получает существующие:
name + description
Если подходящий topic уже существует, Mini должна использовать точное существующее имя.
Например, если уже есть:
Эра / разработка
она не должна без необходимости создавать:
Проект Эра
Архитектура Эры
Разработка Эры
Новый topic создаётся только если существующие действительно не подходят.
16. Таблица memory_topics
Создаётся из:
MemoryItemStore.kt
Структура:
memory_topics

id                  INTEGER PRIMARY KEY AUTOINCREMENT
name                TEXT NOT NULL
description         TEXT NOT NULL
normalized_name     TEXT NOT NULL UNIQUE
created_at          INTEGER NOT NULL
updated_at          INTEGER NOT NULL
normalized_name используется для предотвращения точного повторного создания одной темы.
17. Description смыслового блока
Каждый topic имеет короткое описание области.
Например:
name:
Эра / разработка

description:
Архитектура, технические решения и состояние проекта Эра.
Description нужен в первую очередь Memory Topic Router, чтобы выбирать коробки не только по их короткому названию.
Для существующего topic Compiler должен возвращать существующее описание без изменений.
18. Таблица memory_items
Структура текущей версии:
memory_items

id
topic_id
content
search_text
source_block_id
source_message_ids
compiler_run_id
status
created_at
updated_at
Типы в текущем CREATE TABLE:
id                  INTEGER PRIMARY KEY AUTOINCREMENT
topic_id            INTEGER
content             TEXT NOT NULL
search_text         TEXT NOT NULL
source_block_id     INTEGER NOT NULL
source_message_ids  TEXT NOT NULL
compiler_run_id     INTEGER NOT NULL
status              TEXT NOT NULL
created_at          INTEGER NOT NULL
updated_at          INTEGER NOT NULL
19. Статусы memory_items
Сейчас предусмотрены:
active
superseded
На текущем этапе реально используется главным образом:
active
Полноценный механизм UPDATE → superseded ещё не реализован.
Это одна из ожидаемых точек отказа полевого теста.
20. search_text
Для каждой памяти дополнительно создаётся:
search_text
Он представляет нормализованный content:
lowercase
+
удаление лишней пунктуации
+
оставление букв и цифр
Изначально планировался для локального retrieval.
После перехода на смысловые блоки он пока не играет центральной роли, но остаётся в таблице как потенциальный будущий локальный индекс.
Не удалять до анализа результатов теста.
21. Точная дедупликация
MemoryItemStore уже имеет примитивную защиту от буквально одинаковых записей.
Проверяется:
LOWER(TRIM(content))
среди:
status = active
Это защищает только от точного текстового совпадения.
Например:
Обращаться к пользователю на «ты».
и:
Пользователь предпочитает обращение на «ты».
для текущей системы являются разными воспоминаниями.
Смысловая дедупликация ещё не реализована.
22. Происхождение воспоминания
Каждый memory_item сохраняет:
source_block_id
source_message_ids
compiler_run_id
Поэтому предполагаемая трасса расследования:
memory_items.id
    ↓
compiler_run_id
    ↓
memory_compiler_runs
    ↓
source_block_id
    ↓
raw_blocks
    ↓
start_message_id / end_message_id
    ↓
messages
Кроме того:
source_message_ids
должны указывать непосредственно на сообщения, подтверждающие конкретный атом.
Это критически важная часть архитектуры для анализа заметок.
23. Memory Compiler Run Store
Таблица:
memory_compiler_runs
Она является лабораторным журналом работы Compiler.
В ней ранее использовались поля:
id
raw_block_id
status
input_chars
summary_chars
summary
error
Название:
summary
историческое.
Теперь в summary фактически сохраняется JSON Compiler, а не summary.
То есть поле пока не переименовывалось ради минимизации изменений БД.
24. Исторические тестовые Compiler Runs
Вчера фактически существовали как минимум:
run #1
raw_block_id = 3
status = success
input_chars = 9779
summary_chars = 2183
Это старая версия Compiler, которая ещё делала конспект.
Затем:
run #2
raw_block_id = 4
status = success
input_chars = 10732
summary_chars = 539
Новая версия уже возвращала атомарные memories.
И:
run #3
raw_block_id = 5
status = success
input_chars = 9156
summary_chars = 20
Его полный результат:
{
  "memories": []
}
Это подтвердило, что новый Compiler способен решить:
ничего сохранять не нужно
25. Важная оговорка по старым Compiler Runs
memory_items и memory_topics появились после первых тестов.
Поэтому старые записи memory_compiler_runs сами по себе не означают, что соответствующие атомы автоматически перенесены в новую долговременную память.
При анализе нельзя смешивать:
memory_compiler_runs
и:
memory_items
Первое является журналом запусков Compiler.
Второе является текущим долговременным хранилищем.
26. MemoryItemStore
Компонент:
core/memory/MemoryItemStore.kt
Он отвечает за:
создание memory_topics
создание memory_items
парсинг JSON Compiler
привязку item → topic
получение списка topics
получение items конкретного topic
построение контекста выбранных topics
простую точную дедупликацию
27. MemoryTopic
Текущая Kotlin-модель:
data class MemoryTopic(
    val id: Long,
    val name: String,
    val description: String
)
28. MemoryItem
Текущая Kotlin-модель:
data class MemoryItem(
    val id: Long,
    val topicId: Long,
    val content: String,
    val searchText: String
)
Источник RAW и служебные поля находятся в таблице, но в этой упрощённой Kotlin-модели при чтении topic сейчас не возвращаются.
29. Memory Topic Router
Компонент:
core/memory/MemoryTopicRouter.kt
Модель:
gpt-5-mini
Его задача выполняется перед обычным запросом основной модели.
Router получает:
текущее сообщение пользователя
+
карта memory_topics:
name + description
Он не получает содержимое всей долговременной памяти.
30. Формат ответа Router
Router возвращает:
{
  "topics": [
    "Эра / разработка"
  ]
}
или:
{
  "topics": []
}
Новый topic Router создавать не может.
Название должно совпадать с существующим topic точно.
31. Максимальное число topic на запрос
Сейчас в:
MemoryTopicRouter.kt
установлено:
MAX_SELECTED_TOPICS = 3
То есть один запрос может получить максимум три смысловых блока.
Это экспериментальное ограничение v0.1.
32. Точечное чтение памяти
После Router:
selectedTopics
передаются в:
memoryItemStore.buildTopicContext(selectedTopics)
И только для этих topic локально извлекаются active memory_items.
Пример итогового блока:
Смысловой блок памяти: Пользователь
- Имя пользователя: Артём.
- Обращаться к пользователю на «ты».

Смысловой блок памяти: Эра / разработка
- RAW проекта Эра нельзя переписывать Memory Compiler.
Все остальные topic в запрос основной модели не попадают.
33. Подключение памяти в MainActivity
MainActivity.kt теперь содержит:
MemoryItemStore
MemoryTopicRouter
RawBlockCoordinator
Перед обычным:
openAiClient.sendMessage(...)
происходит:
getTopics()
    ↓
MemoryTopicRouter.route()
    ↓
buildTopicContext()
    ↓
buildSphereInstructionsWithMemory()
    ↓
OpenAiClient
34. Как память передаётся основной модели
Выбранная память добавляется не в message, а в:
instructions
Сначала идут пользовательские инструкции Сферы.
Затем добавляется служебная инструкция примерно такого смысла:
Ниже передан релевантный фрагмент
долгосрочной памяти Эры.

Используй его только как дополнительный
контекст для текущего ответа.

Не упоминай механизм памяти,
если пользователь прямо не спрашивает.

Новое прямое сообщение пользователя
имеет приоритет над старой памятью.
После неё передаётся содержимое выбранных topic.
35. Приоритет новой информации
В MainActivity уже зафиксировано правило:
новое прямое сообщение пользователя
>
старая долговременная память
Это важно, потому что полноценный механизм UPDATE пока отсутствует.
То есть если в памяти:
пользователь живёт в городе A
а пользователь сейчас прямо сообщает:
я переехал в город B
основная модель должна ориентироваться на новое сообщение.
Однако старая запись физически пока может остаться в memory_items.
Именно такие случаи нужно фиксировать заметками.
36. Fail-safe Router
Если MemoryTopicRouter выдаёт ошибку API, JSON или другую ошибку, основной разговор не должен падать.
MainActivity делает fallback:
Router error
    ↓
memoryContext = ""
    ↓
обычный запрос OpenAiClient
Следовательно, возможна ситуация:
Сфера ответила нормально,
но память фактически не использовалась.
Если заметим странное забывание, это нужно учитывать при расследовании.
37. Если topics пока нет
Если:
memoryItemStore.getTopics().isEmpty()
Router вообще не вызывается.
Запрос сразу отправляется основной модели без долговременной памяти.
Это нормальное начальное состояние новой базы.
38. Сохранение нового ответа
После ответа основной модели:
conversationArchive.saveAssistantMessage()
Если сохранение успешно:
lastMessageId = assistantMessageId
и затем:
rawBlockCoordinator.onAssistantMessageSaved(conversationId)
То есть цикл записи памяти запускается автоматически после ответа assistant.
39. Контекст текущего разговора OpenAI
OpenAiClient использует:
private var previousResponseId: String? = null
После успешного ответа:
response.id
становится новым:
previousResponseId
И следующий Responses API запрос содержит:
"previous_response_id": "..."
Это текущий механизм продолжения живого разговора.
Долговременная память Эры работает поверх него независимо.
40. Переключение моделей
В текущей архитектуре OpenAiClient один и тот же объект хранит:
previousResponseId
а setModel() меняет текущую модель.
Практический тест ранее показывал сохранение разговорного контекста при переключении используемых моделей.
Этот механизм отдельно не изменялся при разработке памяти.
41. Пользовательские инструкции
Пользовательские инструкции Сферы хранятся в:
SharedPreferences
era_preferences
sphere_instructions
Долговременная память добавляется к этим инструкциям динамически только на конкретный запрос.
Сама память не записывается в пользовательский текст инструкции.
42. API Key
URI API-ключа хранится:
SharedPreferences
era_preferences
api_key_uri
Memory Compiler, Memory Topic Router и основная модель используют тот же выбранный API-ключ.
43. Локальный backup
В различных точках памяти вызывается:
LocalMemoryBackup.backupInBackground()
Backup вызывается, в частности, после создания RAW Block, создания Compiler Run и после результата Compiler.
Точная реализация LocalMemoryBackup в этот паспорт не включена, потому что текущий файл в этой сессии не анализировался.
44. Известный путь копии базы
Для исследования через Termux использовалась:
~/storage/shared/Download/Era/memory/raw/era_conversation_archive.db
Полный Android-путь:
/storage/emulated/0/Download/Era/memory/raw/era_conversation_archive.db
Важно: это та база/backup, которую мы открывали через sqlite3 для анализа.
45. Заметки исследования
Существующая функция заметок остаётся основным инструментом полевого теста.
Во время использования фиксировать короткими формулировками реальные сбои, например:
Сфера не вспомнила факт.
Router выбрал неправильный смысловой блок.
Compiler сохранил лишнюю информацию.
Compiler не сохранил важную информацию.
Создались два похожих topic.
Создались два одинаковых воспоминания разными словами.
Старая информация не обновилась.
Сфера использовала правильную память неправильно.
Память противоречит новому сообщению.
Сфера вспомнила то, чего пользователь не сообщал.
Не исправлять архитектуру после каждого отдельного случая.
Цель теста:
собрать наблюдаемые ошибки
46. Что пока НЕ реализовано
Сейчас сознательно отсутствуют:
семантическая дедупликация memory_items
NEW / DUPLICATE / UPDATE
автоматическое superseded старой памяти
полноценное разрешение противоречий
поиск атомов внутри большого topic
embeddings
vector database
локальная LLM
граф смысловых связей
оценка confidence
автоматическое объединение похожих topic
автоматическое деление слишком большого topic
точный tokenizer RAW Blocks
собственная кратковременная память
Это не баги реализации v0.1. Это ещё не построенные уровни.
47. Главная гипотеза текущего retrieval
На первом этапе:
запрос
    ↓
GPT-5 mini Router
    ↓
выбор 0–3 смысловых коробок
    ↓
вся активная память выбранной коробки
    ↓
основная модель
То есть мы не отправляем всю долговременную память.
Но внутри выбранного topic пока отправляется вся его активная память.
В будущем, если topic станет слишком большим:
topic
    ↓
локальный/семантический поиск
    ↓
несколько атомов
будет добавлен вторым уровнем.
48. Главный принцип масштабирования
Зафиксированный архитектурный принцип:
Размер всей памяти
не должен определять
размер контекста одной операции.
Сейчас это обеспечивается хотя бы на уровне выбора смысловых блоков.
В дальнейшем тот же принцип должен применяться и внутри больших topic.
49. Известный архитектурный риск №1
GPT-5 mini сама создаёт названия topic.
Даже с передачей существующей карты возможна смысловая фрагментация:
Эра / разработка
Проект Эра
Архитектура Эры
Промпт пытается это запрещать, но гарантии нет.
При появлении такого случая сделать заметку и сохранить конкретные ID.
50. Известный архитектурный риск №2
Дедупликация сейчас только буквальная.
Следовательно, последовательные RAW Blocks могут создать:
Обращаться на «ты».
Пользователь предпочитает обращение на «ты».
Использовать неформальное обращение.
Это ожидаемый объект исследования.
51. Известный архитектурный риск №3
UPDATE пока отсутствует.
Если пользователь меняет факт:
старое значение
    ↓
новое значение
Compiler может просто создать второй memory_item.
Тогда один topic будет содержать противоречие.
Основной модели дано правило считать новое сообщение более приоритетным, но сама база пока автоматически не очищается.
52. Известный архитектурный риск №4
Router видит только:
topic.name
topic.description
Он не видит содержимое items при маршрутизации.
Поэтому возможна ситуация:
нужная память существует
↓
но description topic недостаточно хорошо её отражает
↓
Router не выбирает topic
↓
Сфера «забывает»
Такой случай нужно отдельно отличать от:
Compiler вообще не сохранил память
53. Известный архитектурный риск №5
При ошибке Router запрос молча продолжает работать без памяти.
Для пользователя это может выглядеть просто как:
Сфера почему-то забыла.
Поэтому после обнаружения такого случая при техническом анализе надо проверять Router и состояние API.
Сейчас отдельного журнала Router Runs ещё нет.
Это потенциальное улучшение после полевого теста.
54. Что анализировать после 1–2 дней
Когда тест заканчивается, сначала не меняем код.
Берём:
этот паспорт
+
все заметки
+
SQLite
и для каждого случая определяем точку отказа:
RAW
↓
RAW Block
↓
Compiler
↓
memory_topics
↓
memory_items
↓
Router
↓
selected topic
↓
memory context
↓
основная модель
↓
ответ
Нужно выяснить на каком именно переходе произошёл сбой.
55. SQL для проверки смысловых блоков
После теста:
SELECT
    id,
    name,
    description,
    datetime(created_at/1000, 'unixepoch', 'localtime') AS created
FROM memory_topics
ORDER BY id;
Это покажет все созданные GPT-5 mini смысловые коробки.
56. SQL для просмотра долговременной памяти
SELECT
    mi.id,
    mt.name AS topic,
    mi.content,
    mi.source_block_id,
    mi.source_message_ids,
    mi.compiler_run_id,
    mi.status
FROM memory_items mi
LEFT JOIN memory_topics mt
    ON mt.id = mi.topic_id
ORDER BY mi.id;
Это один из главных запросов будущего анализа.
57. SQL для просмотра конкретного topic
Например:
SELECT
    mi.id,
    mi.content,
    mi.source_message_ids,
    mi.compiler_run_id,
    mi.status
FROM memory_items mi
JOIN memory_topics mt
    ON mt.id = mi.topic_id
WHERE mt.name = 'Эра / разработка'
ORDER BY mi.id;
Название заменить на реальное из memory_topics.
58. SQL для трассировки memory → Compiler
Если странная память имеет, например:
memory_items.id = 12
сначала:
SELECT
    id,
    topic_id,
    content,
    source_block_id,
    source_message_ids,
    compiler_run_id,
    status
FROM memory_items
WHERE id = 12;
Потом по compiler_run_id:
SELECT
    id,
    raw_block_id,
    status,
    input_chars,
    summary_chars,
    summary,
    error
FROM memory_compiler_runs
WHERE id = <compiler_run_id>;
59. SQL для трассировки Compiler → RAW Block
По raw_block_id:
SELECT
    id,
    conversation_id,
    start_message_id,
    end_message_id,
    estimated_tokens,
    status,
    created_at,
    processed_at
FROM raw_blocks
WHERE id = <raw_block_id>;
60. SQL для получения исходного разговора
Когда известны:
start_message_id
end_message_id
выполнить:
SELECT
    id,
    role,
    text
FROM messages
WHERE id BETWEEN <start_message_id> AND <end_message_id>
ORDER BY id;
А если memory_item уже содержит точные:
source_message_ids
можно сразу смотреть конкретные сообщения.
61. Проверка количества памяти по topic
SELECT
    mt.id,
    mt.name,
    COUNT(mi.id) AS memory_count
FROM memory_topics mt
LEFT JOIN memory_items mi
    ON mi.topic_id = mt.id
    AND mi.status = 'active'
GROUP BY mt.id, mt.name
ORDER BY memory_count DESC;
Это покажет, какие коробки начинают разрастаться.
62. Проверка потенциальных точных дублей
SELECT
    LOWER(TRIM(content)) AS normalized_content,
    COUNT(*) AS count
FROM memory_items
WHERE status = 'active'
GROUP BY LOWER(TRIM(content))
HAVING COUNT(*) > 1;
Смысловые дубли этот запрос не найдёт.
63. Проверка последних Compiler Runs
SELECT
    id,
    raw_block_id,
    status,
    input_chars,
    summary_chars,
    error
FROM memory_compiler_runs
ORDER BY id DESC
LIMIT 20;
64. Что пока невозможно точно увидеть
Мы пока не сохраняем журнал работы MemoryTopicRouter.
То есть SQLite сейчас не сообщает:
какое сообщение пришло
какие topics Router увидел
какие topics он выбрал
какой memoryContext реально был отправлен основной модели
Поэтому если основной вид ошибки окажется:
Router достаёт не ту память
то после полевого теста вероятнее всего первым техническим улучшением станет:
memory_router_runs
Это пока сознательно не добавляли.
65. Состояние UI и остального приложения
Память добавлена поверх уже работающего собственного AI-интерфейса.
Сохраняются прежние компоненты:
собственный чат
Responses API
выбор моделей
пользовательские инструкции
previous_response_id
статистика usage
cached tokens
расчёт стоимости
боковое меню
ручные исследовательские заметки
RAW archive
LocalMemoryBackup
Память не должна ломать эти системы.
66. Текущая точка STOP POINT
Готово технически:
Conversation
→ RAW
→ RAW Blocks
→ Memory Compiler
→ atomic memory candidates
→ dynamic semantic topics
→ memory_items
→ Memory Topic Router
→ selected semantic blocks
→ memory context
→ Conversation Model
→ response
→ RAW
Это первый полный цикл собственной долговременной памяти Эры.
67. Следующий этап разработки
Не продолжать архитектурную разработку без причины.
Следующий этап:
реальное использование 1–2 дня
+
ручные заметки
+
сбор наблюдаемых ошибок
Тест можно завершить раньше, если обнаружится явная системная ошибка, которая делает дальнейшее использование бессмысленным.
После теста:
Паспорт 12.08.2026
+
заметки
+
SQLite
+
при необходимости исходный код
используются для расследования.
68. Главный вопрос после полевого теста
Не:
Как улучшить память вообще?
А:
Где конкретно ломается текущий цикл?
То есть отдельно определить:
Compiler extraction problem?
Topic classification problem?
Duplicate problem?
Update problem?
Router problem?
Topic description problem?
Context injection problem?
Main model interpretation problem?
И только после этого менять соответствующий слой.
FINAL STOP POINT / 12.08.2026
Мы перешли от:
RAW → GPT-5 mini → summary
к полноценному экспериментальному циклу:
RAW
↓
RAW Blocks
↓
GPT-5 mini Memory Compiler
↓
атомарные воспоминания
↓
динамические смысловые блоки
↓
локальная долговременная память
↓
GPT-5 mini Memory Topic Router
↓
релевантные смысловые блоки
↓
основная модель Сферы
Основная инженерная идея текущей версии:
Сфера не получает всю долгосрочную память. Сначала GPT-5 mini выбирает максимум три релевантных смысловых блока по их имени и описанию. Только содержимое выбранных блоков добавляется к контексту основной модели.
Память остаётся локальной и трассируемой до RAW-первоисточника.
Следующая работа начинается не с нового кода, а с анализа реального поведения этой версии.
И перед коммитом на GitHub я бы проверил одну единственную вещь руками: что в RawBlockManager.kt действительно уже стоит 4000, а не оставшиеся тестовые 500. Это сейчас единственная известная мне неопределённость в фактическом состоянии паспорта. 🔒🧠

============================================================
ОБНОВЛЕНИЕ ПАСПОРТА v0.2 — 13.08.2026
============================================================

Статус: semantic retrieval для обычного диалога добавлен в код. RAW archive, Memory Compiler и текущий Responses API context сохранены. Этот раздел является актуальным описанием пути чтения памяти; прежний Memory Topic Router больше не используется в критическом пути обычного запроса.

1. Фактический полный поток

Пользовательский запрос
→ сохранение user-сообщения в messages
→ фоновый запрос embedding для запроса
→ локальный cosine search по актуальным embeddings активных memory_items
→ threshold + сортировка + лимиты
→ структурированный memory context только выбранных items
→ обычный streaming Responses API запрос основной модели
→ сохранение assistant-сообщения в messages
→ RawBlockCoordinator
→ raw_blocks
→ Memory Compiler
→ memory_items / memory_topics
→ фоновый embedding/backfill.

previous_response_id остаётся частью текущего API-контекста и не смешивается с долговременной памятью Эры. RAW остаётся первичным источником и не заменяется embedding-индексом.

2. Что уже работало до v0.2

Рабочими до этого этапа были: messages и переносимый SQLite archive; закрытие raw_blocks после приблизительно 4000 токенов и ответа assistant; Memory Compiler на gpt-5-mini через Responses API; memory_compiler_runs со статусами running, success, error; создание memory_topics и active memory_items с source block/message IDs; LocalMemoryBackup; выбор topic через MemoryTopicRouter и добавление выбранной topic-памяти в instructions.

3. Новые компоненты

- OpenAiEmbeddingClient.kt: POST /v1/embeddings, модель text-embedding-3-small. Ключ читается из существующего URI-файла и не логируется.
- MemoryEmbeddingStore.kt: SQLite-таблица memory_embeddings и миграционно-безопасный CREATE TABLE IF NOT EXISTS.
- MemoryEmbeddingIndexer.kt: фоновый lazy/backfill максимум 3 items за запуск; вызывается при отправке запроса и после успешного compiler output.
- SemanticMemoryRetriever.kt: embedding текущего запроса, локальный отбор и диагностическое логирование.
- MemoryRetrievalSelector.kt и EmbeddingMath.kt: чистые cosine/sort/threshold/limit функции.
- MemoryContextBuilder.kt: ограниченный структурированный блок для instructions.

4. SQLite

messages — RAW user/assistant сообщения.
raw_blocks — диапазоны сообщений, ready/processed и служебные поля.
memory_compiler_runs — запуск compiler, input, summary, running/success/error.
memory_topics — карта смысловых тем.
memory_items — атомарные долговременные воспоминания, source и active/superseded status.
research_notes — отдельные пользовательские заметки.
memory_embeddings — дополнительный индекс: memory_item_id UNIQUE, model, model_version, content_hash, vector JSON, timestamps.

Существующая база не пересоздаётся и не очищается. Таблица embeddings создаётся поверх неё. Индекс переносим вместе с SQLite и может быть полностью восстановлен из memory_items.

5. Embeddings и backfill

Embedding единицы memory_item создаётся для его content моделью text-embedding-3-small. Версия индекса: text-embedding-3-small-v1. Content hash SHA-256 предотвращает повторное создание актуального embedding и делает старую запись устаревшей при изменении content. Модель и версия сохранены в таблице для будущей переиндексации.

При обычной отправке сначала запускается неблокирующий backfill до 3 отсутствующих/устаревших items, а retrieval работает по уже доступному индексу. После успешного Memory Compiler запускается такой же фоновой backfill. Ошибка backfill не задерживает и не ломает ответ.

6. Semantic retrieval

Метрика: cosine similarity. Минимальный similarity threshold: 0.78. Максимум: 5 memory_items. Максимальный объём memory context: 4000 символов. Сначала кандидаты сортируются по score по убыванию, затем применяется limit. При пустом индексе, несовпадении размерности, слабом score или ошибке embeddings выбирается 0 items. В основную модель не отправляется вся база, весь RAW или весь список memory_items.

Диагностика Logcat использует tag EraMemoryRetrieval и показывает количество active items, готовность embedding запроса, candidates, selected и scores без vectors и ключей. Ошибка даёт явный fallback without memory.

7. Передача в основную модель

Выбранные записи передаются отдельным блоком [Долговременная память Эры] ... [/Долговременная память Эры] внутри instructions. Инструкция говорит считать блок дополнительным структурированным контекстом, не словами пользователя, использовать только при релевантности, не упоминать retrieval без прямого вопроса и отдавать приоритет новой явной информации пользователя при конфликте. Сам user message и previous_response_id остаются отдельными.

8. Ошибки и ограничения

Любая ошибка чтения SQLite или Embeddings API приводит к обычному запросу без memory context. Основной streaming chat не зависит от compiler, backfill или наличия memory_embeddings. Threshold 0.78 и лимиты являются стартовыми значениями и требуют проверки на реальных разговорах. Backfill сейчас последовательный и ограничен тремя items за вызов; при пустом/недоступном API-ключе он просто завершается с диагностикой. Legacy MemoryTopicRouter и topic-context код сохранены для совместимости и compiler-карты, но не вызываются обычным retrieval-путём.

9. Проверки

ExampleUnitTest покрывает cosine similarity, прохождение похожего кандидата, отсечение нерелевантного кандидата, сортировку/ограничение и пустой список. Интеграционная проверка на телефоне должна подтвердить: compiler создаёт item; после доступного API-ключа item получает запись memory_embeddings; близкий новый запрос показывает selected score в Logcat; далёкий запрос передаётся без memory; отказ embeddings не мешает обычному ответу; backup содержит SQLite вместе с memory_embeddings.
