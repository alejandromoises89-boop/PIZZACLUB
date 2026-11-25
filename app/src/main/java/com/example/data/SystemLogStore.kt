package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

enum class SystemLogLevel(val labelEs: String) {
    ERROR("ERROR"),
    WARN("ADVERTENCIA"),
    INFO("INFO")
}

data class SystemLogEntry(
    val id: String,
    val timestampMs: Long,
    val level: SystemLogLevel,
    val source: String,
    val message: String,
    val detail: String? = null,
    val hint: String? = null
)

object SystemLogDiagnostics {
    const val MAX_LOG_ENTRIES = 100

    fun buildDiagnosticHint(rawError: String): String? {
        val lower = rawError.lowercase(Locale.ROOT)
        return when {
            "401" in lower || "403" in lower || "invalid api key" in lower || "jwt" in lower || "permission denied" in lower ->
                "Verificá tu Anon Key en Supabase (Base Online) y las políticas RLS de supabase_schema.sql."
            "404" in lower || "pgrst205" in lower || "does not exist" in lower || "relation" in lower ->
                "Falta crear la tabla o esquema en Supabase: ejecutá supabase_schema.sql en el SQL Editor."
            "unknownhost" in lower || "failed to connect" in lower || "unable to resolve host" in lower ||
                "connectexception" in lower || "network" in lower || "failed to fetch" in lower ->
                "Sin acceso a internet o URL de Supabase incorrecta (verificá https://...supabase.co)."
            "timeout" in lower || "timed out" in lower ->
                "Tiempo de espera agotado al contactar Supabase. Revisá la estabilidad de tu conexión."
            "json" in lower || "unexpected" in lower ->
                "La respuesta del servidor no tuvo formato JSON válido (revisá la URL del proyecto Supabase)."
            else -> null
        }
    }

    fun appendBounded(
        existing: List<SystemLogEntry>,
        entry: SystemLogEntry,
        maxEntries: Int = MAX_LOG_ENTRIES
    ): List<SystemLogEntry> {
        return (listOf(entry) + existing).take(maxEntries.coerceAtLeast(1))
    }

    fun formatForClipboard(entries: List<SystemLogEntry>): String {
        if (entries.isEmpty()) return "Sin logs de sistema registrados."
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("America/Asuncion")
        }
        return entries.joinToString("\n\n") { e ->
            buildString {
                append("[${df.format(Date(e.timestampMs))}] [${e.level.name}] ${e.source}: ${e.message}")
                if (!e.detail.isNullOrBlank()) append("\nDetalle: ${e.detail}")
                if (!e.hint.isNullOrBlank()) append("\nSugerencia: ${e.hint}")
            }
        }
    }
}

class SystemLogStore private constructor(context: Context) {
    private val prefs by lazy {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val lock = Any()
    private val _logs = MutableStateFlow<List<SystemLogEntry>>(emptyList())
    val logs: StateFlow<List<SystemLogEntry>> = _logs.asStateFlow()

    init {
        _logs.value = readFromPrefs()
    }

    fun logError(source: String, message: String, throwable: Throwable? = null, detail: String? = null) {
        val rawDetail = detail ?: throwable?.let { "${it.javaClass.simpleName}: ${it.message ?: ""}".trim() }
        record(SystemLogLevel.ERROR, source, message, rawDetail)
    }

    fun logWarn(source: String, message: String, detail: String? = null) {
        record(SystemLogLevel.WARN, source, message, detail)
    }

    fun logInfo(source: String, message: String, detail: String? = null) {
        record(SystemLogLevel.INFO, source, message, detail)
    }

    private fun record(level: SystemLogLevel, source: String, message: String, detail: String?) {
        val cleanDetail = detail?.trim()?.take(400)?.ifBlank { null }
        val hintSource = listOfNotNull(message, cleanDetail).joinToString(" ")
        val entry = SystemLogEntry(
            id = "syslog_${UUID.randomUUID().toString().replace("-", "").take(12)}",
            timestampMs = System.currentTimeMillis(),
            level = level,
            source = source.trim().take(60),
            message = message.trim().take(240),
            detail = cleanDetail,
            hint = if (level == SystemLogLevel.INFO) null else SystemLogDiagnostics.buildDiagnosticHint(hintSource)
        )
        synchronized(lock) {
            val next = SystemLogDiagnostics.appendBounded(_logs.value, entry)
            _logs.value = next
            writeToPrefs(next)
        }
    }

    fun clear() {
        synchronized(lock) {
            _logs.value = emptyList()
            prefs.edit().remove(KEY_LOGS_JSON).apply()
        }
    }

    private fun readFromPrefs(): List<SystemLogEntry> {
        val raw = prefs.getString(KEY_LOGS_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val lvl = runCatching { SystemLogLevel.valueOf(o.optString("level", "ERROR")) }
                        .getOrDefault(SystemLogLevel.ERROR)
                    add(
                        SystemLogEntry(
                            id = o.optString("id"),
                            timestampMs = o.optLong("timestampMs", 0L),
                            level = lvl,
                            source = o.optString("source"),
                            message = o.optString("message"),
                            detail = o.optString("detail").ifBlank { null },
                            hint = o.optString("hint").ifBlank { null }
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeToPrefs(entries: List<SystemLogEntry>) {
        val arr = JSONArray()
        for (e in entries) {
            arr.put(
                JSONObject().apply {
                    put("id", e.id)
                    put("timestampMs", e.timestampMs)
                    put("level", e.level.name)
                    put("source", e.source)
                    put("message", e.message)
                    put("detail", e.detail ?: "")
                    put("hint", e.hint ?: "")
                }
            )
        }
        prefs.edit().putString(KEY_LOGS_JSON, arr.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "pizza_club_system_logs_prefs"
        private const val KEY_LOGS_JSON = "logs_json_v1"

        @Volatile
        private var INSTANCE: SystemLogStore? = null

        fun getInstance(context: Context): SystemLogStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SystemLogStore(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
