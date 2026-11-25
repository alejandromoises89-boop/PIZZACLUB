package com.example.ui.admin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.BusinessSettingsModel
import com.example.data.DefaultCatalogDefaults
import com.example.data.InventoryModel
import com.example.data.PaymentAuditLogModel
import com.example.data.ProductModel
import com.example.data.SupabaseRestClient
import com.example.data.VapidKeyBundle
import com.example.data.VapidKeyGenerator
import com.example.data.formatPyg
import com.example.ui.components.QrCodeCard
import com.example.ui.theme.CheeseContainer
import com.example.ui.theme.CheeseSecondary
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DangerRedContainer
import com.example.ui.theme.EspressoDark
import com.example.ui.theme.OnCheeseContainer
import com.example.ui.theme.WhatsAppGreen
import com.example.ui.theme.WhatsAppGreenContainer
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Composable
fun AdminInventoryAndProductsTab(
    products: List<ProductModel>,
    inventory: List<InventoryModel>,
    isBusy: Boolean,
    onAdjustStock: (String, String, Long, Long) -> Unit,
    onSaveProduct: (String, String, String, Long, Boolean, String, Long, Boolean) -> Unit
) {
    var editingProduct by remember { mutableStateOf<ProductModel?>(null) }
    var isCreatingProduct by remember { mutableStateOf(false) }

    val invMap = remember(inventory) { inventory.associateBy { it.id } }

    LazyColumn(
        modifier = Modifier.fillMaxSize().widthIn(max = 840.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Inventario por Sabor", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { isCreatingProduct = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Nuevo Sabor")
                }
            }
        }

        items(products, key = { it.id }) { product ->
            val inv = invMap[product.id]
            val stock = inv?.stock ?: 0L
            val threshold = inv?.lowStockThreshold ?: 5L
            val isLow = stock <= threshold

            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(product.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${formatPyg(product.pricePyg)} · ${if (product.active) "Activo" else "Oculto"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { editingProduct = product }) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Editar ${product.name}")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = if (isLow) DangerRedContainer else WhatsAppGreenContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = if (isLow) "Stock bajo: $stock u." else "Stock: $stock u.",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isLow) DangerRed else WhatsAppGreen,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = {
                                    if (stock > 0L) onAdjustStock(product.id, product.name, stock - 1L, threshold)
                                },
                                enabled = !isBusy && stock > 0L
                            ) {
                                Icon(Icons.Rounded.Remove, contentDescription = "Restar stock")
                            }
                            OutlinedButton(
                                onClick = {
                                    onAdjustStock(product.id, product.name, stock + 1L, threshold)
                                },
                                enabled = !isBusy
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = "Sumar stock")
                            }
                            OutlinedButton(
                                onClick = {
                                    onAdjustStock(product.id, product.name, stock + 10L, threshold)
                                },
                                enabled = !isBusy
                            ) {
                                Text("+10")
                            }
                        }
                    }
                }
            }
        }
    }

    if (editingProduct != null || isCreatingProduct) {
        val existing = editingProduct
        var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
        var desc by remember(existing) { mutableStateOf(existing?.description ?: "") }
        var priceText by remember(existing) { mutableStateOf((existing?.pricePyg ?: 50000L).toString()) }
        var active by remember(existing) { mutableStateOf(existing?.active ?: true) }

        AlertDialog(
            onDismissRequest = {
                editingProduct = null
                isCreatingProduct = false
            },
            title = { Text(if (existing == null) "Nuevo sabor de pizza" else "Editar ${existing.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nombre del sabor") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("Descripción") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Precio (PYG)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Visible en catálogo", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = active, onCheckedChange = { active = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val price = priceText.toLongOrNull() ?: 50000L
                        val cleanId = existing?.id ?: name.trim().lowercase()
                            .replace(Regex("[^a-z0-9]+"), "_")
                            .trim('_')
                            .ifBlank { "pizza_${System.currentTimeMillis()}" }
                        onSaveProduct(
                            cleanId,
                            name,
                            desc,
                            price,
                            active,
                            existing?.imageKey ?: "mozzarella",
                            existing?.sortOrder ?: (products.size + 1).toLong(),
                            existing == null
                        )
                        editingProduct = null
                        isCreatingProduct = false
                    },
                    enabled = name.isNotBlank() && (priceText.toLongOrNull() ?: 0L) > 0L
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    editingProduct = null
                    isCreatingProduct = false
                }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun AdminSettingsAndAuditTab(
    settings: BusinessSettingsModel,
    auditLogs: List<PaymentAuditLogModel>,
    isBusy: Boolean,
    onSaveSettings: (BusinessSettingsModel) -> Unit
) {
    val context = LocalContext.current
    var brandName by remember(settings) { mutableStateOf(settings.brandName) }
    var instagram by remember(settings) { mutableStateOf(settings.instagramHandle) }
    var whatsapp by remember(settings) { mutableStateOf(settings.whatsappNumber) }
    var bankName by remember(settings) { mutableStateOf(settings.bankName) }
    var paymentAlias by remember(settings) { mutableStateOf(settings.paymentAlias) }
    var acceptingOrders by remember(settings) { mutableStateOf(settings.acceptingOrders) }
    var treasureCode by remember(settings) { mutableStateOf(settings.treasureCode) }
    var treasureReward by remember(settings) { mutableStateOf(settings.treasureReward) }
    var googleReviewUrl by remember(settings) { mutableStateOf(settings.googleReviewUrl) }
    var pwaInstallUrl by remember(settings) { mutableStateOf(settings.pwaInstallUrl) }

    val dateFormatter = remember {
        SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("America/Asuncion")
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().widthIn(max = 840.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            CloudDatabaseAndPinCard(context = context)
        }

        item {
            VapidPushSetupCard(context = context)
        }

        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Configuración del Negocio", style = MaterialTheme.typography.titleMedium)

                    OutlinedTextField(
                        value = brandName,
                        onValueChange = { brandName = it },
                        label = { Text("Nombre de la marca") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = instagram,
                        onValueChange = { instagram = it },
                        label = { Text("Instagram") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = whatsapp,
                        onValueChange = { whatsapp = it.filter { ch -> ch.isDigit() } },
                        label = { Text("WhatsApp (sin +)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = bankName,
                        onValueChange = { bankName = it },
                        label = { Text("Banco") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = paymentAlias,
                        onValueChange = { paymentAlias = it },
                        label = { Text("Alias para transferencias") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Premio Sorpresa (QR en la caja)", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = treasureCode,
                        onValueChange = { treasureCode = it.uppercase().take(40) },
                        label = { Text("Código secreto del QR") },
                        placeholder = { Text("Ej: PIZZACLUB-ORO") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_treasure_code_input")
                    )
                    OutlinedTextField(
                        value = treasureReward,
                        onValueChange = { treasureReward = it.take(160) },
                        label = { Text("Premio sorpresa") },
                        placeholder = { Text(DefaultCatalogDefaults.defaultSettings.treasureReward) },
                        minLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_treasure_reward_input")
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Google Reseñas e iPhone / Web", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = googleReviewUrl,
                        onValueChange = { googleReviewUrl = it.take(300) },
                        label = { Text("Link de Reseñas en Google (g.page / Maps)") },
                        placeholder = { Text(DefaultCatalogDefaults.defaultSettings.googleReviewUrl) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_google_review_url_input")
                    )
                    OutlinedTextField(
                        value = pwaInstallUrl,
                        onValueChange = { pwaInstallUrl = it.take(300) },
                        label = { Text("Link Web / iPhone (PWA)") },
                        placeholder = { Text(DefaultCatalogDefaults.defaultSettings.pwaInstallUrl) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_pwa_url_input")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Recibiendo pedidos en la tienda", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = acceptingOrders, onCheckedChange = { acceptingOrders = it })
                    }
                    Button(
                        onClick = {
                            onSaveSettings(
                                settings.copy(
                                    brandName = brandName,
                                    instagramHandle = instagram,
                                    whatsappNumber = whatsapp,
                                    bankName = bankName,
                                    paymentAlias = paymentAlias,
                                    acceptingOrders = acceptingOrders,
                                    treasureCode = treasureCode.trim().uppercase()
                                        .ifBlank { DefaultCatalogDefaults.defaultSettings.treasureCode },
                                    treasureReward = treasureReward.trim()
                                        .ifBlank { DefaultCatalogDefaults.defaultSettings.treasureReward },
                                    googleReviewUrl = googleReviewUrl.trim()
                                        .ifBlank { DefaultCatalogDefaults.defaultSettings.googleReviewUrl },
                                    pwaInstallUrl = pwaInstallUrl.trim()
                                        .ifBlank { DefaultCatalogDefaults.defaultSettings.pwaInstallUrl }
                                )
                            )
                        },
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("admin_save_settings_button")
                    ) {
                        Text("Guardar Configuración")
                    }
                }
            }
        }

        // Box Surprise QR Preview for Printing
        item {
            val activeCode = treasureCode.trim().uppercase()
                .ifBlank { DefaultCatalogDefaults.defaultSettings.treasureCode }
            val activeReward = treasureReward.trim()
                .ifBlank { DefaultCatalogDefaults.defaultSettings.treasureReward }
            val activeBrand = brandName.trim().ifBlank { settings.brandName }
            val activeWa = whatsapp.trim().ifBlank { settings.whatsappNumber }
            val boxQrUrl = remember(activeWa, activeBrand, activeCode) {
                val msg = "🏆 ¡Encontré el QR Secreto de $activeBrand! Código: $activeCode"
                "https://wa.me/$activeWa?text=${URLEncoder.encode(msg, "UTF-8")}"
            }

            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = CheeseContainer),
                border = BorderStroke(1.5.dp, CheeseSecondary)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.EmojiEvents,
                            contentDescription = null,
                            tint = OnCheeseContainer
                        )
                        Text(
                            text = "QR Sorpresa para Imprimir en la Caja",
                            style = MaterialTheme.typography.titleMedium,
                            color = OnCheeseContainer
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        QrCodeCard(
                            content = boxQrUrl,
                            contentDescription = "QR Sorpresa para imprimir en la caja con código $activeCode",
                            size = 132.dp,
                            darkColor = EspressoDark
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Pegá o imprimí este QR en la tapa interior de la caja.",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnCheeseContainer
                            )
                            Text(
                                text = "Código: $activeCode",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = EspressoDark
                            )
                            Text(
                                text = "Premio: $activeReward",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnCheeseContainer
                            )
                            OutlinedButton(
                                onClick = {
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(boxQrUrl)))
                                    } catch (_: Exception) { }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("admin_test_box_qr_button")
                            ) {
                                Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Probar link QR")
                            }
                        }
                    }
                }
            }
        }

        // Multiplatform Launch & Marketing Kit Card
        item {
            val activeBrand = brandName.trim().ifBlank { settings.brandName }
            val activeIg = instagram.trim().ifBlank { settings.instagramHandle }
            val activeReviewUrl = googleReviewUrl.trim()
                .ifBlank { DefaultCatalogDefaults.defaultSettings.googleReviewUrl }
            val activePwaUrl = pwaInstallUrl.trim()
                .ifBlank { DefaultCatalogDefaults.defaultSettings.pwaInstallUrl }
            val promoCopy = remember(activeBrand, activeIg, activePwaUrl, treasureReward) {
                buildString {
                    appendLine("🍕 ¡Llegó la App Oficial de $activeBrand!")
                    appendLine("Pedí tus pizzas artesanales en Ciudad del Este en 30 segundos, sin registrarte ni bajar nada pesado.")
                    appendLine("📲 Abrí e instalá en iPhone o Android: $activePwaUrl")
                    appendLine("🎁 P.D.: Dentro de cada pizza hay un premio sorpresa ($treasureReward). ¡Escaneá el QR de tu caja!")
                    append("Seguinos en Instagram: $activeIg")
                }
            }

            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Campaign, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Kit de Publicidad y Lanzamiento", style = MaterialTheme.typography.titleMedium)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            QrCodeCard(
                                content = activePwaUrl,
                                contentDescription = "QR para iPhone y Android",
                                size = 116.dp,
                                darkColor = EspressoDark
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Rounded.PhoneIphone, contentDescription = null, modifier = Modifier.size(14.dp))
                                Text("QR iPhone / Web", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            QrCodeCard(
                                content = activeReviewUrl,
                                contentDescription = "QR para Reseñas en Google",
                                size = 116.dp,
                                darkColor = EspressoDark
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Rounded.Star, contentDescription = null, tint = CheeseSecondary, modifier = Modifier.size(14.dp))
                                Text("QR Reseñas Google", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Campaña $activeBrand", promoCopy))
                                Toast.makeText(context, "Texto publicitario copiado", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("admin_copy_promo_button")
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copiar Ad")
                        }
                        Button(
                            onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Lanzamiento $activeBrand")
                                    putExtra(Intent.EXTRA_TEXT, promoCopy)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Publicitar $activeBrand"))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen, contentColor = Color.White),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("admin_share_promo_button")
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Compartir")
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            Text("Registro de Auditoría (Inmutable)", style = MaterialTheme.typography.titleMedium)
        }

        if (auditLogs.isEmpty()) {
            item {
                Text("Sin eventos de auditoría registrados todavía.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(auditLogs, key = { it.id }) { log ->
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${log.action} · ${log.orderNumber}", style = MaterialTheme.typography.labelLarge)
                            Text(
                                text = log.createdAt?.toDate()?.let { dateFormatter.format(it) } ?: "Ahora",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${log.previousStatus} → ${log.newStatus} (${formatPyg(log.amountPyg)})",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                        )
                        if (!log.note.isNullOrBlank()) {
                            Text(log.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                        Text("Admin: ${log.adminEmail}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun CloudDatabaseAndPinCard(context: Context) {
    val restClient = remember(context) { SupabaseRestClient(context) }
    val initialCfg = remember(context) { restClient.currentConfig() }
    var supabaseUrl by remember { mutableStateOf(if (initialCfg.isConfigured) initialCfg.url else "") }
    var supabaseKey by remember { mutableStateOf(if (initialCfg.isConfigured) initialCfg.anonKey else "") }
    var isConnected by remember { mutableStateOf(initialCfg.isConfigured) }
    var newPin by remember { mutableStateOf("") }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (isConnected) WhatsAppGreen.copy(alpha = 0.5f) else CheeseSecondary)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Base de Datos Online (Supabase)", style = MaterialTheme.typography.titleMedium)
                Surface(
                    color = if (isConnected) WhatsAppGreenContainer else CheeseContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = if (isConnected) "Online activo" else "Solo local",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isConnected) WhatsAppGreen else OnCheeseContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = if (isConnected) {
                    "Los pedidos de Android, iPhone y Web se sincronizan en vivo cada 5 segundos."
                } else {
                    "Pegá tu Project URL y Anon Key de Supabase (tras ejecutar supabase_schema.sql) para ver pedidos online desde cualquier dispositivo."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = supabaseUrl,
                onValueChange = { supabaseUrl = it },
                label = { Text("Supabase Project URL") },
                placeholder = { Text("https://xxxxx.supabase.co") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("admin_supabase_url_input")
            )

            OutlinedTextField(
                value = supabaseKey,
                onValueChange = { supabaseKey = it },
                label = { Text("Supabase Anon Key") },
                placeholder = { Text("eyJ...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("admin_supabase_key_input")
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val cleanUrl = supabaseUrl.trim().trimEnd('/')
                        val cleanKey = supabaseKey.trim()
                        if (!cleanUrl.startsWith("https://") || cleanKey.length <= 20) {
                            Toast.makeText(context, "Ingresá una URL https:// y Anon Key válida de Supabase.", Toast.LENGTH_LONG).show()
                        } else {
                            restClient.saveRuntimeConfig(cleanUrl, cleanKey)
                            isConnected = restClient.isConfigured()
                            Toast.makeText(context, "Base de datos online conectada.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp).testTag("admin_save_supabase_button")
                ) {
                    Text("Conectar Online")
                }
                if (isConnected) {
                    OutlinedButton(
                        onClick = {
                            restClient.clearRuntimeConfig()
                            val refreshed = restClient.currentConfig()
                            isConnected = refreshed.isConfigured
                            supabaseUrl = if (refreshed.isConfigured) refreshed.url else ""
                            supabaseKey = if (refreshed.isConfigured) refreshed.anonKey else ""
                            Toast.makeText(context, "Configuración local borrada.", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Limpiar")
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text("Cambiar PIN de Administrador", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { newPin = it.filter { ch -> ch.isDigit() }.take(8) },
                    label = { Text("Nuevo PIN (mín. 4 dígitos)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("admin_change_pin_input")
                )
                Button(
                    onClick = {
                        saveCustomAdminPin(context, newPin)
                        newPin = ""
                        Toast.makeText(context, "PIN de administrador actualizado.", Toast.LENGTH_SHORT).show()
                    },
                    enabled = newPin.length >= 4,
                    modifier = Modifier.height(52.dp).testTag("admin_change_pin_button")
                ) {
                    Text("Guardar PIN")
                }
            }
        }
    }
}

@Composable
private fun VapidPushSetupCard(context: Context) {
    val restClient = remember(context) { SupabaseRestClient(context) }
    val cloudCfg = remember(context) { restClient.currentConfig() }
    var vapidBundle by remember { mutableStateOf<VapidKeyBundle?>(null) }
    var vapidSubject by remember { mutableStateOf(VapidKeyBundle.DEFAULT_VAPID_SUBJECT) }
    var showGuide by remember { mutableStateOf(true) }
    var pendingSaveContent by remember { mutableStateOf<String?>(null) }

    val saveDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val content = pendingSaveContent
        pendingSaveContent = null
        if (uri != null && content != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "Archivo guardado correctamente.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error al guardar archivo: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun ensureBundle(): VapidKeyBundle {
        val current = vapidBundle
        if (current != null && current.subject == vapidSubject.trim().ifBlank { VapidKeyBundle.DEFAULT_VAPID_SUBJECT }) {
            return current
        }
        val generated = if (current != null) {
            current.copy(subject = vapidSubject.trim().ifBlank { VapidKeyBundle.DEFAULT_VAPID_SUBJECT })
        } else {
            VapidKeyGenerator.generate(vapidSubject)
        }
        vapidBundle = generated
        return generated
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
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
                    Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Notificaciones Push (VAPID y Supabase)", style = MaterialTheme.typography.titleMedium)
                }
                IconButton(
                    onClick = { showGuide = !showGuide },
                    modifier = Modifier.testTag("admin_toggle_vapid_guide_button")
                ) {
                    Icon(
                        imageVector = if (showGuide) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (showGuide) "Ocultar guía VAPID" else "Mostrar guía VAPID"
                    )
                }
            }

            Text(
                text = "Generá claves VAPID P-256 reales y descargá los archivos listos para habilitar alertas push con la app cerrada en Android y iPhone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = vapidSubject,
                onValueChange = { vapidSubject = it },
                label = { Text("VAPID Subject (mailto:tu@correo.com)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("admin_vapid_subject_input")
            )

            Button(
                onClick = {
                    vapidBundle = VapidKeyGenerator.generate(vapidSubject)
                    Toast.makeText(context, "Nuevas claves VAPID generadas.", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("admin_generate_vapid_button")
            ) {
                Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (vapidBundle == null) "Generar VAPID Keys" else "Regenerar VAPID Keys")
            }

            val activeBundle = vapidBundle
            if (activeBundle != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        VapidKeyPreviewRow(
                            label = "VAPID_PUBLIC_KEY",
                            value = activeBundle.publicKey,
                            onCopy = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("VAPID_PUBLIC_KEY", activeBundle.publicKey))
                                Toast.makeText(context, "VAPID_PUBLIC_KEY copiada", Toast.LENGTH_SHORT).show()
                            }
                        )
                        HorizontalDivider()
                        VapidKeyPreviewRow(
                            label = "VAPID_PRIVATE_KEY (secreta)",
                            value = activeBundle.privateKey,
                            onCopy = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("VAPID_PRIVATE_KEY", activeBundle.privateKey))
                                Toast.makeText(context, "VAPID_PRIVATE_KEY copiada", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val b = ensureBundle()
                            pendingSaveContent = b.toSupabaseSecretsEnv()
                            saveDocumentLauncher.launch("supabase-push-secrets.env")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("admin_download_vapid_env_button")
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Guardar .env", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = {
                            val b = ensureBundle()
                            val cfg = restClient.currentConfig()
                            pendingSaveContent = b.toWebDbConfigJs(cfg.url, cfg.anonKey)
                            saveDocumentLauncher.launch("db-config.js")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("admin_download_db_config_button")
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("db-config.js", maxLines = 1)
                    }
                }

                Button(
                    onClick = {
                        val b = ensureBundle()
                        val cfg = restClient.currentConfig()
                        val packText = buildString {
                            appendLine("=== ARCHIVO 1: supabase-push-secrets.env ===")
                            appendLine(b.toSupabaseSecretsEnv())
                            appendLine("=== ARCHIVO 2: web-pwa/db-config.js ===")
                            appendLine(b.toWebDbConfigJs(cfg.url, cfg.anonKey))
                            appendLine("=== GUIA RAPIDA ===")
                            appendLine(b.toSupabaseGuideText(cfg.url))
                        }
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Pizza Club - Archivos VAPID Push")
                            putExtra(Intent.EXTRA_TEXT, packText)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Compartir o guardar pack VAPID"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen, contentColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("admin_share_vapid_pack_button")
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Compartir Pack + Guía (.env + db-config.js)")
                }
            }

            AnimatedVisibility(visible = showGuide) {
                Surface(
                    color = CheeseContainer,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, CheeseSecondary)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Guía Rápida: Cargar en Supabase (Android & iPhone)",
                            style = MaterialTheme.typography.titleSmall,
                            color = EspressoDark
                        )
                        Text(
                            text = "1. Tocá 'Generar VAPID Keys' y guardá supabase-push-secrets.env + db-config.js.\n" +
                                "2. En Supabase Dashboard -> Edge Functions -> Secrets (o con CLI: supabase secrets set --env-file supabase-push-secrets.env), cargá VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY y VAPID_SUBJECT.\n" +
                                "3. Desplegá 'order-status-push' y en Database -> Webhooks creá un hook sobre public.order_status (INSERT y UPDATE) apuntando a 'order-status-push'.\n" +
                                "4. Reemplazá web-pwa/db-config.js con el archivo generado y hacé Push a GitHub.\n" +
                                "5. En Android Chrome abrí la web y tocá 'Activar alertas'. En iPhone (iOS 16.4+), primero abrí en Safari -> Compartir -> 'Agregar a pantalla de inicio', abrí la app instalada y tocá 'Activar alertas'.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnCheeseContainer
                        )
                        if (cloudCfg.isConfigured) {
                            val projectRef = cloudCfg.url.removePrefix("https://").substringBefore(".supabase.co")
                            OutlinedButton(
                                onClick = {
                                    val dashUrl = "https://supabase.com/dashboard/project/$projectRef/functions/secrets"
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(dashUrl)))
                                    } catch (_: Exception) { }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text("Abrir Secrets en Supabase Dashboard")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VapidKeyPreviewRow(
    label: String,
    value: String,
    onCopy: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                maxLines = 2
            )
        }
        IconButton(onClick = onCopy) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copiar $label")
        }
    }
}
