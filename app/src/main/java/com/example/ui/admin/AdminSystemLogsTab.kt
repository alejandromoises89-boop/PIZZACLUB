package com.example.ui.admin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.SystemLogDiagnostics
import com.example.data.SystemLogEntry
import com.example.data.SystemLogLevel
import com.example.ui.theme.CheeseContainer
import com.example.ui.theme.CheeseSecondary
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DangerRedContainer
import com.example.ui.theme.OnCheeseContainer
import com.example.ui.theme.PendingAmber
import com.example.ui.theme.PendingAmberContainer
import com.example.ui.theme.WhatsAppGreen
import com.example.ui.theme.WhatsAppGreenContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private enum class SystemLogFilter(val label: String) {
    ALL("Todos"),
    ERROR("Errores"),
    WARN("Advertencias"),
    INFO("Info")
}

@Composable
fun AdminSystemLogsTab(
    logs: List<SystemLogEntry>,
    isCloudConnected: Boolean,
    isBusy: Boolean,
    onRunDiagnostics: () -> Unit,
    onClearLogs: () -> Unit
) {
    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf(SystemLogFilter.ALL) }

    val errorCount = remember(logs) { logs.count { it.level == SystemLogLevel.ERROR } }
    val warnCount = remember(logs) { logs.count { it.level == SystemLogLevel.WARN } }

    val filteredLogs = remember(logs, selectedFilter) {
        when (selectedFilter) {
            SystemLogFilter.ALL -> logs
            SystemLogFilter.ERROR -> logs.filter { it.level == SystemLogLevel.ERROR }
            SystemLogFilter.WARN -> logs.filter { it.level == SystemLogLevel.WARN }
            SystemLogFilter.INFO -> logs.filter { it.level == SystemLogLevel.INFO }
        }
    }

    val dateFormatter = remember {
        SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("America/Asuncion")
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().widthIn(max = 840.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(
                    1.dp,
                    if (errorCount > 0) DangerRed.copy(alpha = 0.45f)
                    else if (isCloudConnected) WhatsAppGreen.copy(alpha = 0.4f)
                    else CheeseSecondary
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.BugReport,
                                contentDescription = null,
                                tint = if (errorCount > 0) DangerRed else MaterialTheme.colorScheme.primary
                            )
                            Text("Logs de Sistema", style = MaterialTheme.typography.titleMedium)
                        }
                        Surface(
                            color = if (errorCount > 0) DangerRedContainer else WhatsAppGreenContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = if (errorCount > 0) "$errorCount errores" else "Sin errores",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (errorCount > 0) DangerRed else WhatsAppGreen,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Text(
                        text = if (isCloudConnected) {
                            "Registra automáticamente errores de sincronización con Supabase o carga del catálogo. Errores: $errorCount · Advertencias: $warnCount."
                        } else {
                            "Modo local activo (sin URL/Key de Supabase configurada). Configurá Supabase en Ajustes o ejecutá un diagnóstico."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = onRunDiagnostics,
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("admin_run_diagnostics_button")
                    ) {
                        Icon(Icons.Rounded.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Probar conexión Supabase y catálogo")
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val text = SystemLogDiagnostics.formatForClipboard(filteredLogs)
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Pizza Club Logs", text))
                                Toast.makeText(context, "Logs copiados al portapapeles", Toast.LENGTH_SHORT).show()
                            },
                            enabled = filteredLogs.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("admin_copy_system_logs_button")
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copiar")
                        }
                        OutlinedButton(
                            onClick = {
                                onClearLogs()
                                Toast.makeText(context, "Logs de sistema limpiados", Toast.LENGTH_SHORT).show()
                            },
                            enabled = logs.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("admin_clear_system_logs_button")
                        ) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Limpiar")
                        }
                    }
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SystemLogFilter.entries) { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter.label) }
                    )
                }
            }
        }

        if (filteredLogs.isEmpty()) {
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = WhatsAppGreen,
                            modifier = Modifier.size(34.dp)
                        )
                        Text(
                            text = "Todo funcionando sin incidencias",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Cualquier fallo al cargar productos o sincronizar pedidos con Supabase aparecerá aquí al instante.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(filteredLogs, key = { it.id }) { entry ->
                SystemLogEntryCard(entry = entry, dateFormatter = dateFormatter)
            }
        }
    }
}

@Composable
private fun SystemLogEntryCard(
    entry: SystemLogEntry,
    dateFormatter: SimpleDateFormat
) {
    val (badgeBg, badgeFg, icon) = when (entry.level) {
        SystemLogLevel.ERROR -> Triple(DangerRedContainer, DangerRed, Icons.Rounded.ErrorOutline)
        SystemLogLevel.WARN -> Triple(PendingAmberContainer, PendingAmber, Icons.Rounded.Warning)
        SystemLogLevel.INFO -> Triple(WhatsAppGreenContainer, WhatsAppGreen, Icons.Rounded.Info)
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (entry.level == SystemLogLevel.ERROR) BorderStroke(1.dp, DangerRed.copy(alpha = 0.35f)) else null
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(color = badgeBg, shape = MaterialTheme.shapes.small) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(icon, contentDescription = null, tint = badgeFg, modifier = Modifier.size(14.dp))
                        Text(
                            text = "${entry.level.labelEs} · ${entry.source}",
                            style = MaterialTheme.typography.labelMedium,
                            color = badgeFg
                        )
                    }
                }
                Text(
                    text = dateFormatter.format(Date(entry.timestampMs)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
            )

            if (!entry.detail.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = entry.detail,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.fillMaxWidth().padding(8.dp)
                    )
                }
            }

            if (!entry.hint.isNullOrBlank()) {
                Surface(
                    color = CheeseContainer,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, CheeseSecondary)
                ) {
                    Text(
                        text = "Diagnóstico: ${entry.hint}",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnCheeseContainer,
                        modifier = Modifier.fillMaxWidth().padding(8.dp)
                    )
                }
            }
        }
    }
}
