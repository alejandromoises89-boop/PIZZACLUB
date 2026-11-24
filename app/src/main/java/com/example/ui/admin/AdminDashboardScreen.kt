package com.example.ui.admin

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.BusinessSettingsModel
import com.example.data.FulfillmentType
import com.example.data.OrderModel
import com.example.data.OrderStatus
import com.example.data.buildAdminCustomerThankYouMessage
import com.example.data.formatPyg
import com.example.ui.components.OrderStatusBadge
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DangerRedContainer
import com.example.ui.theme.PendingAmber
import com.example.ui.theme.PendingAmberContainer
import com.example.ui.theme.WhatsAppGreen
import com.example.ui.theme.WhatsAppGreenContainer
import com.example.viewmodel.AdminDashboardUiState
import com.example.viewmodel.AdminViewModel
import com.example.viewmodel.OrderFilterTab
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    viewModel: AdminViewModel,
    adminEmail: String,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.dashboardState.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedOrderFilter.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var orderToConfirm by remember { mutableStateOf<OrderModel?>(null) }
    var orderToCancel by remember { mutableStateOf<OrderModel?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(actionMessage) {
        if (!actionMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(actionMessage!!)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Admin Header Bar
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Admin Pizza Club", style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = adminEmail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = onSignOut,
                            modifier = Modifier.testTag("admin_signout_button")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = "Cerrar sesión")
                        }
                    }

                    val tabs = listOf("Resumen", "Pedidos", "Stock y Menú", "Ajustes")
                    PrimaryTabRow(selectedTabIndex = selectedTab) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                    }
                }
            }

            when (val state = uiState) {
                is AdminDashboardUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is AdminDashboardUiState.Error -> {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                    }
                }
                is AdminDashboardUiState.Ready -> {
                    when (selectedTab) {
                        0 -> AdminOverviewTab(
                            state = state,
                            onJumpToPendingOrders = {
                                viewModel.setOrderFilter(OrderFilterTab.PENDING_PAYMENT)
                                selectedTab = 1
                            },
                            onJumpToStock = { selectedTab = 2 }
                        )
                        1 -> AdminOrdersTab(
                            orders = state.orders,
                            settings = state.settings,
                            selectedFilter = selectedFilter,
                            isBusy = isBusy,
                            onSelectFilter = viewModel::setOrderFilter,
                            onRequestConfirmPayment = { orderToConfirm = it },
                            onAdvanceStatus = viewModel::advanceStatus,
                            onRequestCancel = { orderToCancel = it },
                            onExportCsv = {
                                val csv = viewModel.buildOrdersCsv(state.orders)
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/csv"
                                    putExtra(Intent.EXTRA_SUBJECT, "Pizza Club - Historial de Pedidos")
                                    putExtra(Intent.EXTRA_TEXT, csv)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Exportar pedidos CSV"))
                            },
                            onOpenMaps = { url ->
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                } catch (_: Exception) { }
                            },
                            onSendWhatsAppThanks = { order ->
                                val phone = normalizeParaguayWhatsAppPhone(order.customerPhone)
                                val msg = buildAdminCustomerThankYouMessage(order, state.settings)
                                val encoded = URLEncoder.encode(msg, "UTF-8")
                                val url = "https://wa.me/$phone?text=$encoded"
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                } catch (_: Exception) { }
                            }
                        )
                        2 -> AdminInventoryAndProductsTab(
                            products = state.products,
                            inventory = state.inventory,
                            isBusy = isBusy,
                            onAdjustStock = viewModel::adjustStock,
                            onSaveProduct = viewModel::saveProduct
                        )
                        3 -> AdminSettingsAndAuditTab(
                            settings = state.settings,
                            auditLogs = state.auditLogs,
                            isBusy = isBusy,
                            onSaveSettings = viewModel::saveBusinessSettings
                        )
                    }
                }
            }
        }
    }

    // Explicit Payment Confirmation Dialog (Prevents accidental or unverified approval)
    if (orderToConfirm != null) {
        val target = orderToConfirm!!
        var note by remember(target.id) { mutableStateOf("Transferencia Itaú verificada") }
        AlertDialog(
            onDismissRequest = { orderToConfirm = null },
            icon = { Icon(Icons.Rounded.Verified, contentDescription = null, tint = WhatsAppGreen) },
            title = { Text("Confirmar depósito bancario") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "¿Verificaste en tu cuenta de ${target.paymentBank} (${target.paymentAlias}) el ingreso exacto de ${formatPyg(target.totalAmountPyg)} para el pedido ${target.orderNumber}?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Esta acción descontará el stock atómicamente y desbloqueará el botón de WhatsApp para ${target.customerName}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Nota de auditoría") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.confirmPayment(target.id, note)
                        orderToConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen, contentColor = Color.White),
                    modifier = Modifier.testTag("dialog_confirm_payment_yes")
                ) {
                    Text("CONFIRMAR PAGO")
                }
            },
            dismissButton = {
                TextButton(onClick = { orderToConfirm = null }) {
                    Text("Volver")
                }
            }
        )
    }

    // Cancel Order Dialog
    if (orderToCancel != null) {
        val target = orderToCancel!!
        var reason by remember(target.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { orderToCancel = null },
            title = { Text("Cancelar pedido ${target.orderNumber}") },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Motivo de cancelación") },
                    placeholder = { Text("Ej: No se recibió la transferencia") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.cancelOrder(target, reason)
                        orderToCancel = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed, contentColor = Color.White)
                ) {
                    Text("Cancelar Pedido")
                }
            },
            dismissButton = {
                TextButton(onClick = { orderToCancel = null }) {
                    Text("Volver")
                }
            }
        )
    }
}

@Composable
private fun AdminOverviewTab(
    state: AdminDashboardUiState.Ready,
    onJumpToPendingOrders: () -> Unit,
    onJumpToStock: () -> Unit
) {
    val kpis = state.kpis
    LazyColumn(
        modifier = Modifier.fillMaxSize().widthIn(max = 840.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (kpis.pendingPaymentCount > 0) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = PendingAmberContainer),
                    shape = MaterialTheme.shapes.large
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${kpis.pendingPaymentCount} ${if (kpis.pendingPaymentCount == 1) "pedido espera" else "pedidos esperan"} verificación de pago",
                                style = MaterialTheme.typography.titleMedium,
                                color = PendingAmber
                            )
                            Text(
                                text = "Verificá la transferencia Itaú antes de confirmar.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = onJumpToPendingOrders) {
                            Text("Revisar")
                        }
                    }
                }
            }
        }

        if (kpis.lowStockCount > 0) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DangerRedContainer),
                    shape = MaterialTheme.shapes.large
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Warning, contentDescription = null, tint = DangerRed)
                            Text(
                                text = "${kpis.lowStockCount} sabores con stock bajo",
                                style = MaterialTheme.typography.titleSmall,
                                color = DangerRed
                            )
                        }
                        TextButton(onClick = onJumpToStock) { Text("Ajustar") }
                    }
                }
            }
        }

        item {
            Text("Métricas de Ventas (PYG)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiCard("Hoy", formatPyg(kpis.dailySalesPyg), Modifier.weight(1f))
                KpiCard("Semana", formatPyg(kpis.weeklySalesPyg), Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiCard("Mes", formatPyg(kpis.monthlySalesPyg), Modifier.weight(1f))
                KpiCard("Ingresos Totales", formatPyg(kpis.totalRevenuePyg), Modifier.weight(1f), highlight = true)
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiCard("Pedidos activos", "${kpis.activePipelineCount}", Modifier.weight(1f))
                KpiCard("Total órdenes", "${state.orders.size}", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun KpiCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun AdminOrdersTab(
    orders: List<OrderModel>,
    settings: BusinessSettingsModel,
    selectedFilter: OrderFilterTab,
    isBusy: Boolean,
    onSelectFilter: (OrderFilterTab) -> Unit,
    onRequestConfirmPayment: (OrderModel) -> Unit,
    onAdvanceStatus: (OrderModel, OrderStatus) -> Unit,
    onRequestCancel: (OrderModel) -> Unit,
    onExportCsv: () -> Unit,
    onOpenMaps: (String) -> Unit,
    onSendWhatsAppThanks: (OrderModel) -> Unit
) {
    val filtered = remember(orders, selectedFilter) {
        when (selectedFilter) {
            OrderFilterTab.ALL -> orders
            OrderFilterTab.PENDING_PAYMENT -> orders.filter { it.status == OrderStatus.PENDING_PAYMENT.rawValue }
            OrderFilterTab.CONFIRMED -> orders.filter { it.status == OrderStatus.PAYMENT_CONFIRMED.rawValue }
            OrderFilterTab.IN_PROGRESS -> orders.filter {
                it.status in setOf(
                    OrderStatus.PREPARING.rawValue,
                    OrderStatus.READY_FOR_PICKUP.rawValue,
                    OrderStatus.OUT_FOR_DELIVERY.rawValue
                )
            }
            OrderFilterTab.COMPLETED -> orders.filter { it.status == OrderStatus.COMPLETED.rawValue }
            OrderFilterTab.CANCELLED -> orders.filter { it.status == OrderStatus.CANCELLED.rawValue }
        }
    }

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
                Text("Pedidos (${filtered.size})", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(
                    onClick = onExportCsv,
                    enabled = orders.isNotEmpty(),
                    modifier = Modifier.testTag("export_csv_button")
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Exportar CSV")
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(OrderFilterTab.entries) { tab ->
                    FilterChip(
                        selected = selectedFilter == tab,
                        onClick = { onSelectFilter(tab) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No hay pedidos en esta categoría.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(filtered, key = { it.id }) { order ->
                AdminOrderCard(
                    order = order,
                    isBusy = isBusy,
                    onConfirmPayment = { onRequestConfirmPayment(order) },
                    onAdvanceStatus = { next -> onAdvanceStatus(order, next) },
                    onCancel = { onRequestCancel(order) },
                    onOpenMaps = onOpenMaps,
                    onSendWhatsAppThanks = { onSendWhatsAppThanks(order) }
                )
            }
        }
    }
}

@Composable
private fun AdminOrderCard(
    order: OrderModel,
    isBusy: Boolean,
    onConfirmPayment: () -> Unit,
    onAdvanceStatus: (OrderStatus) -> Unit,
    onCancel: () -> Unit,
    onOpenMaps: (String) -> Unit,
    onSendWhatsAppThanks: () -> Unit
) {
    val status = order.statusEnum()
    val fulfillment = order.fulfillmentEnum()

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(order.orderNumber, style = MaterialTheme.typography.titleMedium)
                    Text("${order.customerName} · ${order.customerPhone}", style = MaterialTheme.typography.bodySmall)
                }
                OrderStatusBadge(status = status)
            }

            Text(
                text = "${order.preparationMode} · ${fulfillment.label}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )

            if (fulfillment == FulfillmentType.DELIVERY) {
                Text(
                    text = "Dirección: ${order.neighborhood.orEmpty()}, ${order.streetAndNumber.orEmpty()}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (!order.deliveryReference.isNullOrBlank()) {
                    Text("Ref.: ${order.deliveryReference}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!order.googleMapsLink.isNullOrBlank()) {
                    TextButton(
                        onClick = { onOpenMaps(order.googleMapsLink) },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Rounded.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Abrir ubicación en Maps")
                    }
                }
            } else {
                Text(
                    text = "Retiro: ${order.pickupDate ?: order.desiredDate} ${order.pickupTime.orEmpty()}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (!order.additionalInstructions.isNullOrBlank()) {
                Text(
                    text = "Nota: ${order.additionalInstructions}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()
            order.items.forEach { item ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${item.quantity}x ${item.productName}", style = MaterialTheme.typography.bodyMedium)
                    Text(formatPyg(item.lineTotalPyg), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total", style = MaterialTheme.typography.titleSmall)
                Text(formatPyg(order.totalAmountPyg), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
            }

            if (status == OrderStatus.PENDING_PAYMENT) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onCancel,
                        enabled = !isBusy,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("Rechazar")
                    }
                    Button(
                        onClick = onConfirmPayment,
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen, contentColor = Color.White),
                        modifier = Modifier
                            .weight(1.6f)
                            .height(48.dp)
                            .testTag("admin_confirm_payment_${order.id}")
                    ) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("CONFIRMAR PAGO")
                    }
                }
            } else if (status != OrderStatus.COMPLETED && status != OrderStatus.CANCELLED) {
                val nextStatus = when (status) {
                    OrderStatus.PAYMENT_CONFIRMED -> OrderStatus.PREPARING
                    OrderStatus.PREPARING -> if (fulfillment == FulfillmentType.DELIVERY) OrderStatus.OUT_FOR_DELIVERY else OrderStatus.READY_FOR_PICKUP
                    OrderStatus.READY_FOR_PICKUP, OrderStatus.OUT_FOR_DELIVERY -> OrderStatus.COMPLETED
                    else -> null
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onCancel,
                        enabled = !isBusy,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("Cancelar")
                    }
                    if (nextStatus != null) {
                        Button(
                            onClick = { onAdvanceStatus(nextStatus) },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1.6f).height(48.dp)
                        ) {
                            Text("Pasar a: ${nextStatus.labelEs}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            if (status in setOf(
                    OrderStatus.PAYMENT_CONFIRMED,
                    OrderStatus.PREPARING,
                    OrderStatus.READY_FOR_PICKUP,
                    OrderStatus.OUT_FOR_DELIVERY,
                    OrderStatus.COMPLETED
                )
            ) {
                OutlinedButton(
                    onClick = onSendWhatsAppThanks,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("admin_send_wa_thanks_${order.id}"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = WhatsAppGreen)
                ) {
                    Icon(Icons.Rounded.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Enviar agradecimiento WA")
                }
            }
        }
    }
}

private fun normalizeParaguayWhatsAppPhone(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    return when {
        digits.startsWith("595") -> digits
        digits.startsWith("0") -> "595" + digits.drop(1)
        digits.length in 7..9 -> "595$digits"
        else -> digits
    }
}
