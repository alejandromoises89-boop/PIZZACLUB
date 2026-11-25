package com.example

import com.example.data.SystemLogDiagnostics
import com.example.data.SystemLogEntry
import com.example.data.SystemLogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemLogStoreTest {

    @Test
    fun diagnosticHintsIdentifyCommonSupabaseAndConnectivityErrors() {
        val authHint = SystemLogDiagnostics.buildDiagnosticHint("HTTP 401: Invalid API key")
        assertNotNull(authHint)
        assertTrue(authHint!!.contains("Anon Key"))

        val schemaHint = SystemLogDiagnostics.buildDiagnosticHint("HTTP 404: PGRST205 relation public.products does not exist")
        assertNotNull(schemaHint)
        assertTrue(schemaHint!!.contains("supabase_schema.sql"))

        val netHint = SystemLogDiagnostics.buildDiagnosticHint("UnknownHostException: Unable to resolve host")
        assertNotNull(netHint)
        assertTrue(netHint!!.contains("internet"))
    }

    @Test
    fun appendBoundedKeepsNewestEntriesFirstAndCapsAtMaxSize() {
        var list = emptyList<SystemLogEntry>()
        for (i in 1..105) {
            list = SystemLogDiagnostics.appendBounded(
                list,
                SystemLogEntry(
                    id = "log_$i",
                    timestampMs = i * 1000L,
                    level = SystemLogLevel.ERROR,
                    source = "Supabase",
                    message = "Error #$i"
                ),
                maxEntries = 100
            )
        }
        assertEquals(100, list.size)
        assertEquals("log_105", list.first().id)
        assertEquals("log_6", list.last().id)
    }

    @Test
    fun formatForClipboardIncludesLevelSourceMessageDetailAndHint() {
        val formatted = SystemLogDiagnostics.formatForClipboard(
            listOf(
                SystemLogEntry(
                    id = "l1",
                    timestampMs = 1_700_000_000_000L,
                    level = SystemLogLevel.ERROR,
                    source = "Carga Productos",
                    message = "Fallo al cargar catálogo",
                    detail = "HTTP 404",
                    hint = "Ejecutá supabase_schema.sql"
                )
            )
        )
        assertTrue(formatted.contains("[ERROR] Carga Productos: Fallo al cargar catálogo"))
        assertTrue(formatted.contains("Detalle: HTTP 404"))
        assertTrue(formatted.contains("Sugerencia: Ejecutá supabase_schema.sql"))
    }
}
