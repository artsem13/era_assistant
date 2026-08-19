# xAI Search v0.1 Empirical Probe

## 1. Executive conclusion

**READY_FOR_IMPLEMENTATION_SPEC**

Для отдельного production-ТЗ достаточно фактических данных по Web Search, X Search, usage/cost и routing. Рекомендуемый дешёвый режим для обычного поиска: `grok-4.3 + reasoning.effort=low`. X Search следует парсить по фактическому Responses output: в этом probe он вернул `custom_tool_call` с именами server-side X operations, а не `x_search_call`.

## 2. Environment

- Дата probe: 17 августа 2026 UTC.
- Endpoint: `POST https://api.x.ai/v1/responses`.
- API key загружался напрямую из внешнего secret-файла; в raw/results не записывался. Secret scan: PASS.
- Выполнены: A `grok-4.3/none` CBR, B `grok-4.3/low` CBR, C `grok-4.3/low` X Search, D `grok-4.3/none` natural international news.
- E `grok-4.6/low` не выполнялся: уже имеется baseline `grok-4.6/high`, а дополнительные данные не оправдывали ещё один платный CBR вызов.
- Baseline: `grok-4.6/high`, 4 web calls, 21,565 total tokens, 676,460,000 ticks = $0.067646.
- Все raw JSON находятся в `xai_probe/raw/` и содержат request envelope, timestamps, latency, HTTP status, response и нормализованные observed metrics.

## 3. Probe results

### A — grok-4.3 / none / CBR

Raw: `raw/01_grok43_none_cbr.json`.

Ответ нашёл официальный пресс-релиз CBR: 24 июля 2026 года ставка снижена на 25 б.п. до 14.00%; присутствуют inline URL citations. Было 6 `web_search_call`, 6 server-side calls, 15 уникальных encountered URLs, 2 видимых `url_citation`. `input_tokens=22965`, cached `9664`, output `280`, reasoning `0`, total `23245`; latency 8919.7 ms; `$0.04925905` / `₽4.925905`.

### B — grok-4.3 / low / CBR

Raw: `raw/02_grok43_low_cbr.json`.

Факт также корректен: 24 июля 2026 года, 14.00%, действует с 27 июля. Было 2 `web_search_call`, 2 server-side calls, 15 уникальных encountered URLs, 4 visible citations. `input_tokens=6675`, cached `2048`, output `569`, reasoning `429`, total `7244`; latency 11844.9 ms; `$0.01761585` / `₽1.761585`.

`low` дал практически достаточное качество и в 2.80 раза меньшую стоимость, хотя latency в этом конкретном повторе была выше.

### C — X Search

Raw: `raw/03_x_search.json`.

Первый запрос с `include=["x_search_call.action.sources"]` вернул HTTP 400: `Argument not supported ... in include field`. Повтор с тем же model/reasoning/tool и без `include` вернул HTTP 200. Это согласуется с observed Responses API: Web Search поддерживает `web_search_call.action.sources`, а для X Search такой include в данном endpoint не поддержан.

Фактически получены 4 X operations: `x_keyword_search` (3) и `x_user_search` (1), представленные как `custom_tool_call`; inputs содержат JSON с `query`, `limit`/`count` и `mode`. Ответ message содержит 10 `url_citation`, включая прямые `x.com/.../status/...` и user URLs. Модель перечислила 3 поста, но даты и handle в тексте модели нельзя считать независимым metadata contract: они были сгенерированы в message.

`input_tokens=18638`, cached `8896`, output `1765`, reasoning `1536`, total `20403`; latency 20503.7 ms; `$0.03836920` / `₽3.836920`.

### D — natural international news

Raw: `raw/04_global_news.json`.

Без `allowed_domains` модель выполнила 16 `web_search_call` и 16 server-side calls. Найдено 86 encountered URL entries, 72 unique URLs и 52 unique domains. Наблюдалась смесь Reuters, WSJ, BBC, NBC, NYT, TechCrunch, Axios, The Information, Google/DeepMind и company sources, но также агрегаторы, Reddit, YouTube, LinkedIn и повторные story-clusters. Поэтому diversity присутствует, однако source selection не является автоматически независимым: один news event может быть представлен несколькими пересказами.

`input_tokens=95167`, cached `62336`, output `1587`, reasoning `0`, total `96754`; latency 27443.5 ms; `$0.13747345` / `₽13.747345`.

## 4. Web Search response contract

Наблюдаемая форма:

```text
response.output[]
  { type: "reasoning", ... }
  { type: "web_search_call", status: "completed",
    action: { type: "search", query: string,
              sources: [{ type: "url", url: string }] } }
  { type: "message", role: "assistant",
    content: [{ type: "output_text", text: string,
                annotations: [{ type: "url_citation", url, title?,
                                start_index, end_index }] }] }
response.usage
  input_tokens, input_tokens_details.cached_tokens,
  output_tokens, output_tokens_details.reasoning_tokens,
  total_tokens, num_server_side_tools_used,
  server_side_tool_usage_details.web_search_calls,
  server_side_tool_usage_details.x_search_calls,
  cost_in_usd_ticks, context_details
```

`action.sources` — encountered/search-provided URLs, not proof that every URL was cited in final text. `message.content[].annotations` — directly cited provenance. `open_page` was not exposed as a separate top-level output type in these new calls; any internal browse/open activity is represented by usage/tool counts and/or search call results, not a guaranteed stable public object. Titles, snippets and dates are not guaranteed in `action.sources`; this probe mainly returned `{type,url}`.

## 5. X Search response contract

For `tools: [{"type":"x_search"}]` the observed output was:

```text
response.output[]
  { type: "custom_tool_call", name: "x_keyword_search",
    call_id: "xs_call-...", input: "{...}", status: "completed" }
  { type: "custom_tool_call", name: "x_user_search", ... }
  { type: "message", content: [{ type: "output_text", text,
      annotations: [{ type: "url_citation", url, title,
                      start_index, end_index }] }] }
```

Observed operation inputs included `{"query":"from:xai Grok","limit":"5","mode":"Latest"}`, `{"query":"xAI","count":"3"}` and similar. There was no `action`/`sources` object and no separate `x_search_call` object in this response. The usage contract still reported `x_search_calls=4` and `web_search_calls=0`.

Direct post URLs were present as citations, including `x.com/i/status/...`; the model text also emitted `https://x.com/SpaceXAI/status/...`. The parser must preserve both annotation URLs and message text URLs, without assuming that timestamps, handles, title or source metadata exist.

## 6. Citation / provenance findings

- Web Search and X Search can share an outer Responses parser over `response.output`, `message.content`, annotations and `usage`.
- They cannot share a single inner tool-call schema: Web uses `web_search_call.action.query/sources`; this X response used `custom_tool_call.name/input`.
- Preserve `encountered_sources` separately from `citations`. A source may be encountered but never cited; citation annotations can include URLs not present in `action.sources`.
- `num_sources_used` is not a reliable count of visible URLs: the baseline and new responses expose many sources/citations while `num_sources_used` may be absent or zero. Use it only as an API-provided metric, never as the parser's source count.
- The minimum lossless internal mapping is feasible: `SearchRun` (request/time/usage/cost), `SearchAction` (outer type, operation, query/input, status), `WebSource` (URL plus optional metadata), `Citation` (URL/title/indices/annotation type) and `EvidenceBundle` (answer plus separate encountered/cited provenance).
- `cost_in_usd_ticks` is authoritative when present. Conversion used here: ticks / 10,000,000,000 = USD; USD × 100 = RUB.

## 7. Source diversity

D returned 52 domains and 72 unique URLs. It included primary/company sources (Google/DeepMind and other official pages), reputable international reporting (Reuters, WSJ, BBC, NBC, NYT, TechCrunch, Axios, The Information), and less independent material (aggregators, Reddit, social/video pages). The result therefore demonstrates source diversity, not source independence or truth by itself.

For Era, provenance should retain `official position` plus `independent reporting` plus `alternative relevant evidence` when the question requires it. Official sources prove what an organization says or does; they do not automatically provide a neutral full account.

## 8. Cost comparison

| Probe | Model | Reasoning | Web calls | X calls | Input | Output | Reasoning tokens | Latency | USD | RUB |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| A | grok-4.3 | none | 6 | 0 | 22,965 | 280 | 0 | 8.920 s | 0.04925905 | 4.925905 |
| B | grok-4.3 | low | 2 | 0 | 6,675 | 569 | 429 | 11.845 s | 0.01761585 | 1.761585 |
| C | grok-4.3 | low | 0 | 4 | 18,638 | 1,765 | 1,536 | 20.504 s | 0.03836920 | 3.836920 |
| D | grok-4.3 | none | 16 | 0 | 95,167 | 1,587 | 0 | 27.444 s | 0.13747345 | 13.747345 |
| **Total** |  |  | **24** | **4** | **143,445** | **4,201** | **1,965** |  | **0.24271755** | **24.271755** |

The two grok-4.3 CBR stages average `$0.03343745` / `₽3.343745` and 10.382 s. Against the previous `grok-4.6/high` baseline ($0.067646), this average is about 2.02× cheaper. B's low cost is associated with fewer calls and fewer input/reasoning tokens. D demonstrates a material risk: a seemingly simple “three news items” request generated 16 web calls, 96,754 total tokens and $0.13747345.

## 9. Recommended xAI production model

For Internet Search v0.1: **`grok-4.3`, `reasoning.effort=low`**.

Reason: B returned the required CBR fact and citations at the lowest measured web-search cost. Use explicit max-turn/call controls in the future production spec; this probe shows that unrestricted natural-news retrieval can expand substantially. X Search may use the same model/reasoning default, but it has a distinct parser contract and higher measured reasoning usage in C.

## 10. Recommended v0.1 search routing

- `GENERAL_WEB` → `web_search`.
- `SOCIAL_REALTIME_X` → `x_search`.
- `BOTH` → both tools only when the user explicitly needs general web evidence and X evidence.

No routing or production code was implemented.

## 11. Usage fields available

Directly available or derivable from observed responses:

- `xai_requests`: one per response request (the response has an id; this probe envelope records one request).
- `web_search_calls`, `x_search_calls`: `usage.server_side_tool_usage_details`.
- `xai_input_tokens`: `usage.input_tokens`.
- `cached_tokens`: `usage.input_tokens_details.cached_tokens`.
- `xai_output_tokens`: `usage.output_tokens`.
- `reasoning_tokens`: `usage.output_tokens_details.reasoning_tokens`.
- `cost_in_usd_ticks`: `usage.cost_in_usd_ticks`.
- actual USD and RUB: derived from authoritative ticks using the project formula.
- `latency_ms`: measured client-side from immediately before POST through response body completion; not an API usage field.

## 12. Remaining unknowns

- Whether future API versions expose a dedicated `x_search_call` shape rather than the observed `custom_tool_call` shape for the same tool.
- Whether X Search will expose stable structured post metadata (author, timestamp, title, snippet) in another documented include or endpoint; this response did not.
- Exact relation between internal `open_page` operations and visible output/usage for all query types.
- Production limits and policy for maximum calls/turns should be specified and tested separately; D proves the cost risk but not the final limit choice.

## 13. Production-spec readiness

The probe now establishes enough for a production Codex-spec to define:

- the Responses endpoint and tool selection;
- model/reasoning defaults;
- sequential request envelope and client-side latency measurement;
- Web versus X parser branches;
- encountered-source versus cited-provenance storage;
- citation URL/title/index preservation with nullable metadata;
- usage and authoritative tick-based cost accounting;
- source-diversity evidence handling and GENERAL_WEB/SOCIAL_REALTIME_X/BOTH routing;
- security rule that raw responses are saved only after secret scan.

Следующий шаг — отдельное production-ТЗ для Codex на интеграцию xAI Search в существующую архитектуру Эры.
