package com.era.assistant.core.blackbox

import org.json.JSONObject
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class BlackBoxWriter(
    private val target: BlackBoxStorage.Target,
    private val formatVersion: Int,
    private val sessionId: String,
    private val profile: BlackBoxProfile,
    private val startedAt: String,
    private val durationRequestedMs: Long,
    private val metadata: Map<String, Any?>
) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "EraBlackBoxWriter").apply { isDaemon = true }
    }
    private var eventCount = 0


    private var closed = false

    init {
        executor.execute {
            val writer = BufferedWriter(OutputStreamWriter(target.output, StandardCharsets.UTF_8))
            writeHeader(writer)
            writer.flush()
            writerRef = writer
        }
    }

    @Volatile
    private var writerRef: BufferedWriter? = null

    fun append(event: BlackBoxEvent) {
        if (closed) return
        executor.execute {
            val writer = writerRef ?: return@execute
            if (eventCount > 0) writer.write(",\n")
            val json = JSONObject()
                .put("event", event.event)
                .put("timestamp", event.timestamp)
                .put("elapsedMs", event.elapsedMs)
                .put("sessionId", event.sessionId)
            event.turnId?.let { json.put("turnId", it) }
            event.generation?.let { json.put("generation", it) }
            event.state?.let { json.put("state", it) }
            val data = JSONObject()
            event.data.forEach { (key, value) ->
                data.put(key, BlackBoxSanitizer.sanitizeEventValue(event.event, key, value))
            }
            json.put("data", data)
            writer.write(json.toString(2).prependIndent("    "))
            eventCount++
            if (eventCount % 20 == 0) writer.flush()
        }
    }

    fun close(endedAt: String, endReason: BlackBoxEndReason) {
        if (closed) return
        closed = true
        executor.execute {
            val writer = writerRef
            if (writer != null) {
                writer.write("\n  ],\n")
                writer.write(JSONObject().put("endedAt", endedAt).toString(2).prependIndent("  ").removeSuffix("\n"))
                writer.write(",\n")
                writer.write(JSONObject().put("endReason", endReason.wireName).toString(2).prependIndent("  ").removeSuffix("\n"))
                writer.write("\n}\n")
                writer.flush()
                writer.close()
            }
            try {
                target.complete()
            } finally {
                executor.shutdown()
            }
        }
    }

    private fun writeHeader(writer: BufferedWriter) {
        val metadataJson = JSONObject()
        metadata.forEach { (key, value) ->
            metadataJson.put(key, BlackBoxSanitizer.sanitizeValue(key, value))
        }
        writer.write("{\n")
        writer.write("  \"formatVersion\": $formatVersion,\n")
        writer.write("  \"sessionId\": ${JSONObject.quote(sessionId)},\n")
        writer.write("  \"profile\": ${JSONObject.quote(profile.wireName)},\n")
        writer.write("  \"startedAt\": ${JSONObject.quote(startedAt)},\n")
        writer.write("  \"durationRequestedMs\": $durationRequestedMs,\n")
        writer.write("  \"metadata\": ${metadataJson.toString(2).prependIndent("  ")},\n")
        writer.write("  \"events\": [\n")
    }
}
