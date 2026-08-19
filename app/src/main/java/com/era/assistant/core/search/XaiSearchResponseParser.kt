package com.era.assistant.core.search

import org.json.JSONArray
import org.json.JSONObject

class XaiSearchResponseParser {
    fun parse(
        responseText: String,
        mode: SearchMode,
        startedAt: String,
        finishedAt: String,
        latencyMs: Long,
        rawReference: String?
    ): EvidenceBundle {
        val root = JSONObject(responseText)
        val output = root.optJSONArray("output") ?: JSONArray()
        val actions = ArrayList<SearchAction>()
        val encountered = ArrayList<WebSource>()
        val citations = ArrayList<Citation>()
        val answer = StringBuilder()

        for (index in 0 until output.length()) {
            val item = output.optJSONObject(index) ?: continue
            val type = item.optString("type", "")
            when (type) {
                "web_search_call" -> {
                    val action = item.optJSONObject("action")
                    val sources = ArrayList<WebSource>()
                    val sourceArray = action?.optJSONArray("sources") ?: JSONArray()
                    for (sourceIndex in 0 until sourceArray.length()) {
                        val source = sourceArray.optJSONObject(sourceIndex) ?: continue
                        val url = source.optString("url", "")
                        if (url.isNotBlank()) {
                            val webSource = WebSource(
                                url = url,
                                title = source.optNullableString("title"),
                                sourceType = source.optNullableString("type"),
                                metadata = source.optJSONObject("metadata")?.toString()
                            )
                            sources.add(webSource)
                            encountered.add(webSource)
                        }
                    }
                    actions.add(SearchAction(type, action?.optNullableString("type"), action?.optNullableString("query"), item.optNullableString("status"), sources))
                }
                "custom_tool_call" -> {
                    actions.add(SearchAction(type, item.optNullableString("name"), item.optNullableString("input"), item.optNullableString("status")))
                }
                "x_search_call" -> {
                    val action = item.optJSONObject("action")
                    val sources = parseSources(action?.optJSONArray("sources"), encountered)
                    actions.add(SearchAction(type, action?.optNullableString("type"), action?.optNullableString("query"), item.optNullableString("status"), sources))
                }
                "message" -> parseMessage(item, answer, citations)
                else -> Unit
            }
        }

        if (answer.isBlank()) throw IllegalStateException("xAI search response has no message")
        val usageJson = root.optJSONObject("usage")
        val detail = usageJson?.optJSONObject("server_side_tool_usage_details")
        val ticks = usageJson?.optNullableLong("cost_in_usd_ticks")
        val usage = SearchUsage(
            inputTokens = usageJson?.optNullableInt("input_tokens"),
            cachedTokens = usageJson?.optJSONObject("input_tokens_details")?.optNullableInt("cached_tokens"),
            outputTokens = usageJson?.optNullableInt("output_tokens"),
            reasoningTokens = usageJson?.optJSONObject("output_tokens_details")?.optNullableInt("reasoning_tokens"),
            totalTokens = usageJson?.optNullableInt("total_tokens"),
            webSearchCalls = detail?.optNullableInt("web_search_calls"),
            xSearchCalls = detail?.optNullableInt("x_search_calls"),
            numServerSideToolsUsed = usageJson?.optNullableInt("num_server_side_tools_used"),
            costInUsdTicks = ticks,
            latencyMs = latencyMs
        )
        val run = SearchRun("xAI", root.optString("model", "grok-4.3"), "low", mode, startedAt, finishedAt, latencyMs, root.optNullableString("id"), usage, SearchCost(usage.usd(), usage.rub(XaiSearchClient.DEFAULT_RUB_PER_USD), ticks), rawReference)
        return EvidenceBundle(answer.toString().trim(), actions, encountered.distinctBy { it.url }, citations.distinctBy { it.url + "|" + it.startIndex + "|" + it.endIndex }, usage, run)
    }

    private fun parseMessage(item: JSONObject, answer: StringBuilder, citations: MutableList<Citation>) {
        val content = item.optJSONArray("content") ?: return
        for (index in 0 until content.length()) {
            val part = content.optJSONObject(index) ?: continue
            if (part.optString("type") != "output_text") continue
            answer.append(part.optString("text", ""))
            val annotations = part.optJSONArray("annotations") ?: JSONArray()
            for (annotationIndex in 0 until annotations.length()) {
                val annotation = annotations.optJSONObject(annotationIndex) ?: continue
                val url = annotation.optString("url", "")
                if (url.isNotBlank()) citations.add(Citation(url, annotation.optNullableString("title"), annotation.optNullableInt("start_index"), annotation.optNullableInt("end_index"), annotation.optString("type", "unknown")))
            }
        }
    }

    private fun parseSources(array: JSONArray?, encountered: MutableList<WebSource>): List<WebSource> {
        val result = ArrayList<WebSource>()
        if (array == null) return result
        for (index in 0 until array.length()) {
            val source = array.optJSONObject(index) ?: continue
            val url = source.optString("url", "")
            if (url.isNotBlank()) {
                val item = WebSource(url, source.optNullableString("title"), source.optNullableString("type"), source.optJSONObject("metadata")?.toString())
                result.add(item); encountered.add(item)
            }
        }
        return result
    }
}

private fun JSONObject.optNullableString(name: String): String? = if (has(name) && !isNull(name)) getString(name) else null
private fun JSONObject.optNullableInt(name: String): Int? = if (has(name) && !isNull(name)) optInt(name) else null
private fun JSONObject.optNullableLong(name: String): Long? = if (has(name) && !isNull(name)) optLong(name) else null
