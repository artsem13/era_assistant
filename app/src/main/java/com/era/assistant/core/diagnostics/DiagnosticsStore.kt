package com.era.assistant.core.diagnostics

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject

data class DiagnosticsSummary(val totalUserMessages: Int, val totalAssistantMessages: Int, val rubertDecisions: Int, val miniRequests: Int, val miniWeb: Int, val miniNoWeb: Int, val miniClarify: Int, val webSearchRequests: Int, val webSearchSuccess: Int, val webSearchFailures: Int, val notesCreated: Int, val notesUpdated: Int, val notesDeleted: Int, val averageMiniDurationMs: Double, val averageWebDurationMs: Double)

class DiagnosticsStore(private val appContext: Context) : SQLiteOpenHelper(appContext.applicationContext, "era_diagnostics.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) { db.execSQL("CREATE TABLE diagnostic_events (id INTEGER PRIMARY KEY AUTOINCREMENT, event_type TEXT NOT NULL, timestamp_epoch_ms INTEGER NOT NULL, local_datetime TEXT NOT NULL, timezone_id TEXT, utc_offset TEXT, conversation_id TEXT, turn_id TEXT, message_id TEXT, payload_json TEXT NOT NULL)") }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    fun insert(event: DiagnosticsEvent) {
        val values = ContentValues().apply {
            put("event_type", event.eventType); put("timestamp_epoch_ms", event.timestampEpochMs); put("local_datetime", event.localDatetime); put("timezone_id", event.timezoneId); put("utc_offset", event.utcOffset); put("conversation_id", event.conversationId); put("turn_id", event.turnId); put("message_id", event.messageId); put("payload_json", event.payload.toString())
        }
        writableDatabase.insertOrThrow("diagnostic_events", null, values)
    }
    fun summary(): DiagnosticsSummary {
        fun count(type: String, extra: String? = null): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM diagnostic_events WHERE event_type=?" + if (extra == null) "" else " AND " + extra, arrayOf(type)).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        fun average(type: String): Double { var total = 0.0; var rows = 0; readableDatabase.query("diagnostic_events", arrayOf("payload_json"), "event_type=?", arrayOf(type), null, null, null).use { c -> while (c.moveToNext()) { val value = JSONObject(c.getString(0)).optDouble("duration_ms", Double.NaN); if (!value.isNaN()) { total += value; rows++ } } }; return if (rows == 0) 0.0 else total / rows }
        val web = "instr(payload_json, '\"decision\":\"WEB\"') > 0"
        val noWeb = "instr(payload_json, '\"decision\":\"NO_WEB\"') > 0"
        val clarify = "instr(payload_json, '\"decision\":\"CLARIFY_USER\"') > 0"
        val success = "instr(payload_json, '\"success\":true') > 0"
        val failure = "instr(payload_json, '\"success\":false') > 0"
        return DiagnosticsSummary(count("USER_MESSAGE"), count("ASSISTANT_MESSAGE"), count("RUBERT_WEB_DECISION"), count("MINI_REQUEST"), count("MINI_RESULT", web), count("MINI_RESULT", noWeb), count("MINI_RESULT", clarify), count("WEB_SEARCH_REQUEST"), count("WEB_SEARCH_RESULT", success), count("WEB_SEARCH_RESULT", failure), count("NOTE_CREATED"), count("NOTE_UPDATED"), count("NOTE_DELETED"), average("MINI_RESULT"), average("WEB_SEARCH_RESULT"))
    }
    fun exportToDownload(): File {
        val directory = File("/storage/emulated/0/Download/Era/diagnostics").apply { mkdirs() }
        val name = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val target = File(directory, "era_diagnostics_$name.db")
        close()
        File(contextFilesDir(), "era_diagnostics.db").copyTo(target, overwrite = true)
        return target
    }
    private fun contextFilesDir(): File = appContext.getDatabasePath("era_diagnostics.db")
}
