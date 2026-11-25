package com.example.ui.customer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.BusinessSettingsModel
import com.example.data.FulfillmentType
import com.example.data.LocalSavedOrder
import com.example.data.OrderStatus
import com.example.data.OrderStatusTrackerModel
import com.example.data.buildWhatsAppOrderMessage
import com.example.data.formatOrderCountdownLabel
import com.example.data.formatPyg
import com.example.data.instagramProfileUrl
import com.example.ui.components.InstagramLogoIcon
import com.example.ui.components.ItauTransferCard
import com.example.ui.components.OrderProgressStepper
import com.example.ui.components.OrderStatusBadge
import com.example.ui.theme.CheeseContainer
import com.example.ui.theme.CheeseSecondary
import com.example.ui.theme.EspressoDark
import com.example.ui.theme.OnCheeseContainer
import com.example.ui.theme.PendingAmber
import com.example.ui.theme.PendingAmberContainer
import com.example.ui.theme.TomatoPrimary
import com.example.ui.theme.WhatsAppGreen
import com.example.ui.theme.WhatsAppGreenContainer
import java.net.URLEncoder

@Composable
fun OrderTrackerScreen(
    savedOrders: List<LocalSavedOrder>,
    selectedOrderId: String?,
    liveTracker: OrderStatusTrackerModel?,
    settings: BusinessSettingsModel,
    isRefreshingHistory: Boolean,
    pizzaClubPoints: Int = 0,
    onSelectOrder: (String) -> Unit,
    onRefreshHistory: () -> Unit,
    onGoToMenu: () -> Unit,
    onOpenTreasureHunt: () -> Unit,
    onOpenSocialQr: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    if (savedOrders.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.widthIn(max = 360.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ReceiptLong,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
                Text(
                    text = "Todavía no realizaste pedidos",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Elegí tus sabores favoritos en el menú, generá tu preorden sin registrarte y seguí el estado en vivo desde acá.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onGoToMenu,
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Explorar Menú")
                }
                OutlinedButton(
                    onClick = onOpenTreasureHunt,
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Rounded.CardGiftcard, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Premio Sorpresa y Redes")
                }
            }
        }
        return
    }

    val activeOrder = savedOrders.firstOrNull { it.orderId == selectedOrderId } ?: savedOrders.first()
    val effectiveStatus = liveTracker?.takeIf { it.id == activeOrder.orderId }?.statusEnum()
        ?: OrderStatus.fromRaw(activeOrder.status)
    val effectiveWhatsappUnlocked = liveTracker?.takeIf { it.id == activeOrder.orderId }?.whatsappUnlocked
        ?: activeOrder.whatsappUnlocked

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 680.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Sección Pizza Club Points (1 punto por cada pizza comprada)
        item {
            PizzaClubPointsCard(
                points = pizzaClubPoints,
                savedOrders = savedOrders
            )
        }

        // Historial completo de pedidos guardados con estado de confirmación por Admin
        item {
            OrderHistorySection(
                savedOrders = savedOrders,
                selectedOrderId = activeOrder.orderId,
                liveTracker = liveTracker,
                isRefreshingHistory = isRefreshingHistory,
                onSelectOrder = onSelectOrder,
                onRefreshHistory = onRefreshHistory
            )
        }

        // PedidosYa-style Celebratory Confirmation + Box QR Secret Teaser
        item {
            OrderCelebrationBanner(
                orderNumber = activeOrder.orderNumber,
                customerName = activeOrder.customerName,
                status = effectiveStatus,
                settings = settings,
                onOpenTreasureHunt = onOpenTreasureHunt,
                onOpenSocialQr = onOpenSocialQr,
                onOpenInstagram = {
                    val igUrl = instagramProfileUrl(settings.instagramHandle)
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(igUrl)))
                    } catch (_: Exception) {
                        onOpenSocialQr()
                    }
                }
            )
        }

        // Status Header + Stepper
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Preorden ${activeOrder.orderNumber}",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = activeOrder.preparationMode,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OrderStatusBadge(status = effectiveStatus)
                    }

                    Spacer(Modifier.height(16.dp))
                    OrderProgressStepper(
                        status = effectiveStatus,
                        fulfillmentType = FulfillmentType.fromRaw(activeOrder.fulfillmentType),
                        stageUpdatedAtMillis = liveTracker?.takeIf { it.id == activeOrder.orderId }?.updatedAt?.toDate()?.time
                            ?: activeOrder.stageUpdatedAtMillis
                    )
                }
            }
        }

        // Bank Transfer Card (shown prominently while pending payment, or accessible as reference)
        item {
            ItauTransferCard(
                bankName = activeOrder.paymentBank,
                paymentAlias = activeOrder.paymentAlias,
                totalAmountPyg = activeOrder.totalAmountPyg,
                orderNumber = activeOrder.orderNumber,
                onCopyAlias = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Alias Itaú", activeOrder.paymentAlias))
                    Toast.makeText(context, "Alias copiado: ${activeOrder.paymentAlias}", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // WhatsApp Gate Card (Unlocked ONLY after admin confirms payment)
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = if (effectiveWhatsappUnlocked) WhatsAppGreenContainer else PendingAmberContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (effectiveWhatsappUnlocked) Icons.AutoMirrored.Rounded.Chat else Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = if (effectiveWhatsappUnlocked) WhatsAppGreen else PendingAmber
                        )
                        Text(
                            text = if (effectiveWhatsappUnlocked) {
                                "¡Pago confirmado! Coordiná tu entrega por WhatsApp"
                            } else {
                                "Esperando confirmación del pago"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = if (effectiveWhatsappUnlocked) WhatsAppGreen else PendingAmber
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (effectiveWhatsappUnlocked) {
                            "Nuestro equipo verificó tu transferencia en ${activeOrder.paymentBank}. Presioná el botón para abrir WhatsApp con todo el detalle de tu pedido."
                        } else {
                            "Realizá la transferencia al alias indicado arriba. Por seguridad, el botón de WhatsApp se desbloquea únicamente cuando nuestro administrador verifica el depósito en ${activeOrder.paymentBank}."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(14.dp))

                    Button(
                        onClick = {
                            val message = buildWhatsAppOrderMessage(
                                orderNumber = activeOrder.orderNumber,
                                customerName = activeOrder.customerName,
                                customerPhone = activeOrder.customerPhone,
                                items = activeOrder.items,
                                preparationMode = activeOrder.preparationMode,
                                fulfillmentType = activeOrder.fulfillmentType,
                                neighborhood = activeOrder.neighborhood,
                                streetAndNumber = activeOrder.streetAndNumber,
                                deliveryReference = activeOrder.deliveryReference,
                                googleMapsLink = activeOrder.googleMapsLink,
                                pickupDate = activeOrder.pickupDate,
                                pickupTime = activeOrder.pickupTime,
                                desiredDate = activeOrder.desiredDate,
                                additionalInstructions = activeOrder.additionalInstructions,
                                totalAmountPyg = activeOrder.totalAmountPyg,
                                paymentStatusLabel = effectiveStatus.labelEs
                            )
                            val encoded = URLEncoder.encode(message, "UTF-8")
                            val url = "https://wa.me/${activeOrder.whatsappNumber}?text=$encoded"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        },
                        enabled = effectiveWhatsappUnlocked && effectiveStatus != OrderStatus.CANCELLED,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("whatsapp_continue_button"),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WhatsAppGreen,
                            contentColor = Color.White,
                            disabledContainerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = if (effectiveWhatsappUnlocked) Icons.AutoMirrored.Rounded.Chat else Icons.Rounded.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (effectiveWhatsappUnlocked) "Continuar por WhatsApp" else "WhatsApp bloqueado hasta confirmar pago",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        // Complete Pre-order Summary
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Detalle de la preorden", style = MaterialTheme.typography.titleMedium)
                    Text("Cliente: ${activeOrder.customerName} (${activeOrder.customerPhone})", style = MaterialTheme.typography.bodyMedium)
                    if (activeOrder.fulfillmentType == FulfillmentType.DELIVERY.rawValue) {
                        Text(
                            "Delivery: ${activeOrder.neighborhood}, ${activeOrder.streetAndNumber}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (activeOrder.deliveryReference.isNotBlank()) {
                            Text("Ref.: ${activeOrder.deliveryReference}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Text(
                            "Retiro en local: ${activeOrder.pickupDate} ${activeOrder.pickupTime}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (activeOrder.additionalInstructions.isNotBlank()) {
                        Text("Instrucciones: ${activeOrder.additionalInstructions}", style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    activeOrder.items.forEach { line ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${line.quantity}x ${line.productName}", style = MaterialTheme.typography.bodyMedium)
                            Text(formatPyg(line.lineTotalPyg), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total", style = MaterialTheme.typography.titleMedium)
                        Text(formatPyg(activeOrder.totalAmountPyg), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderCelebrationBanner(
    orderNumber: String,
    customerName: String,
    status: OrderStatus,
    settings: BusinessSettingsModel,
    onOpenTreasureHunt: () -> Unit,
    onOpenSocialQr: () -> Unit,
    onOpenInstagram: () -> Unit
) {
    val firstName = customerName.trim().substringBefore(" ").ifBlank { "Cliente" }
    val statusHeadline = when (status) {
        OrderStatus.PENDING_PAYMENT -> "¡Gracias por tu preferencia, $firstName!"
        OrderStatus.PAYMENT_CONFIRMED -> "¡Gracias por tu preferencia, $firstName!"
        OrderStatus.PREPARING -> "¡Tu pizza ya está en el horno, $firstName!"
        OrderStatus.READY_FOR_PICKUP -> "¡Tu pedido está listo para retirar!"
        OrderStatus.OUT_FOR_DELIVERY -> "¡Tu pedido va en camino!"
        OrderStatus.COMPLETED -> "¡Disfrutá tu ${settings.brandName}, $firstName!"
        OrderStatus.CANCELLED -> "Pedido $orderNumber cancelado"
    }
    val statusSubtitle = when (status) {
        OrderStatus.PENDING_PAYMENT -> "Tu preorden $orderNumber fue registrada con éxito en ${settings.brandName}. Completá la transferencia y seguí cada paso acá."
        OrderStatus.CANCELLED -> "Este pedido fue cancelado. Si tenés consultas podés escribirnos directo por WhatsApp."
        else -> "Confirmamos tu compra en ${settings.brandName}. ¡Gracias por elegir masa madre artesanal!"
    }

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = EspressoDark),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(EspressoDark, Color(0xFF281B14))
                    )
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = CheeseSecondary,
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = EspressoDark,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = orderNumber,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = EspressoDark
                        )
                    }
                }

                Surface(
                    color = TomatoPrimary,
                    shape = CircleShape,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.CardGiftcard,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = statusHeadline,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Text(
                    text = statusSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.88f)
                )
            }

            // Clean Surprise Box Callout ("Dentro de cada pizza hay un premio")
            Surface(
                color = CheeseContainer,
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.5.dp, CheeseSecondary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CardGiftcard,
                            contentDescription = null,
                            tint = TomatoPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "P.D.: Dentro de cada pizza hay un premio sorpresa. Escaneá el QR que viene en tu caja para descubrirlo.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = OnCheeseContainer
                        )
                    }
                    Button(
                        onClick = onOpenTreasureHunt,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("tracker_open_treasure_hunt"),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TomatoPrimary,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Rounded.CardGiftcard, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Tengo el QR de mi caja")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenInstagram,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("tracker_instagram_button"),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    InstagramLogoIcon(size = 18.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = settings.instagramHandle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onOpenSocialQr,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("tracker_social_qr_button"),
                    border = BorderStroke(1.dp, CheeseSecondary.copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CheeseSecondary)
                ) {
                    Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("QR y iPhone", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun OrderHistorySection(
    savedOrders: List<LocalSavedOrder>,
    selectedOrderId: String,
    liveTracker: OrderStatusTrackerModel?,
    isRefreshingHistory: Boolean,
    onSelectOrder: (String) -> Unit,
    onRefreshHistory: () -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(savedOrders) {
        nowMillis = System.currentTimeMillis()
        while (savedOrders.isNotEmpty()) {
            delay(1_000L)
            nowMillis = System.currentTimeMillis()
        }
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Historial de pedidos (${savedOrders.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Tocá cualquier pedido para ver detalles y si el admin confirmó el pago.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = onRefreshHistory,
                    enabled = !isRefreshingHistory,
                    modifier = Modifier.testTag("refresh_order_history_button")
                ) {
                    if (isRefreshingHistory) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Actualizar historial", modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text("Actualizar")
                }
            }

            savedOrders.forEach { order ->
                val isSelected = order.orderId == selectedOrderId
                val trackerMatch = liveTracker?.takeIf { it.id == order.orderId }
                val effectiveStatus = trackerMatch?.statusEnum() ?: OrderStatus.fromRaw(order.status)
                val isConfirmed = (trackerMatch?.whatsappUnlocked ?: order.whatsappUnlocked) ||
                    (effectiveStatus != OrderStatus.PENDING_PAYMENT && effectiveStatus != OrderStatus.CANCELLED)
                val itemsSummary = order.items.joinToString(", ") { "${it.quantity}x ${it.productName}" }
                val dateLabel = dateFormatter.format(Date(order.createdAtMillis))

                Surface(
                    onClick = { onSelectOrder(order.orderId) },
                    shape = MaterialTheme.shapes.medium,
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("history_order_card_${order.orderNumber}")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = order.orderNumber,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            OrderStatusBadge(status = effectiveStatus)
                        }

                        Text(
                            text = itemsSummary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        val stageMillis = trackerMatch?.updatedAt?.toDate()?.time ?: order.stageUpdatedAtMillis
                        val countdownLabel = formatOrderCountdownLabel(
                            status = effectiveStatus,
                            fulfillmentType = FulfillmentType.fromRaw(order.fulfillmentType),
                            stageStartedAtMillis = stageMillis,
                            nowMillis = nowMillis
                        )
                        if (countdownLabel.isNotBlank() && effectiveStatus != OrderStatus.CANCELLED) {
                            Surface(
                                color = if (isConfirmed) WhatsAppGreenContainer else CheeseContainer,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Timer,
                                        contentDescription = null,
                                        tint = if (isConfirmed) WhatsAppGreen else OnCheeseContainer,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = countdownLabel,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (isConfirmed) WhatsAppGreen else OnCheeseContainer
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isConfirmed) Icons.Rounded.CheckCircle else Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = if (isConfirmed) WhatsAppGreen else PendingAmber,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = when {
                                        effectiveStatus == OrderStatus.CANCELLED -> "Cancelado · $dateLabel"
                                        isConfirmed -> "Confirmado por Admin · $dateLabel"
                                        else -> "Esperando confirmación · $dateLabel"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isConfirmed) WhatsAppGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = formatPyg(order.totalAmountPyg),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PizzaClubPointsCard(
    points: Int,
    savedOrders: List<LocalSavedOrder>
) {
    val goal = 10
    val progress = ((points % goal).toFloat() / goal.toFloat()).let {
        if (points > 0 && points % goal == 0) 1f else it
    }
    val remaining = if (points > 0 && points % goal == 0) 0 else goal - (points % goal)

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = CheeseContainer),
        border = BorderStroke(1.dp, CheeseSecondary),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pizza_club_points_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = TomatoPrimary,
                        shape = CircleShape,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Pizza Club Points",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = OnCheeseContainer
                        )
                        Text(
                            text = "1 punto por cada pizza comprada",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnCheeseContainer.copy(alpha = 0.8f)
                        )
                    }
                }

                Surface(
                    color = EspressoDark,
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = "$points pts",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = CheeseSecondary,
                        modifier = Modifier
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .testTag("pizza_club_points_balance")
                    )
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = TomatoPrimary,
                trackColor = Color.White.copy(alpha = 0.65f)
            )

            Text(
                text = if (remaining == 0 && points > 0) {
                    "¡Meta de $goal puntos alcanzada! Mostrá tu pantalla por WhatsApp para reclamar tu premio VIP."
                } else {
                    "Te faltan $remaining ${if (remaining == 1) "pizza" else "pizzas"} para desbloquear tu próximo premio VIP ($goal pts)."
                },
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = OnCheeseContainer
            )

            val recentEarned = savedOrders
                .filter { it.status != OrderStatus.CANCELLED.rawValue }
                .take(3)
            if (recentEarned.isNotEmpty()) {
                HorizontalDivider(color = CheeseSecondary.copy(alpha = 0.6f))
                recentEarned.forEach { order ->
                    val qty = order.items.sumOf { it.quantity.toInt().coerceAtLeast(0) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${order.orderNumber} ($qty ${if (qty == 1) "pizza" else "pizzas"})",
                            style = MaterialTheme.typography.labelMedium,
                            color = OnCheeseContainer.copy(alpha = 0.85f)
                        )
                        Text(
                            text = "+$qty pts",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = WhatsAppGreen
                        )
                    }
                }
            }
        }
    }
}

