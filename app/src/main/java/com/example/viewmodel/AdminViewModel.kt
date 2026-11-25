package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BusinessSettingsModel
import com.example.data.DefaultCatalogDefaults
import com.example.data.InventoryModel
import com.example.data.OrderModel
import com.example.data.OrderStatus
import com.example.data.PaymentAuditLogModel
import com.example.data.PizzaClubRepository
import com.example.data.ProductModel
import com.example.data.SystemLogEntry
import com.example.data.unwrapBackendMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.cancellation.CancellationException

data class AdminSalesKpis(
    val dailySalesPyg: Long = 0L,
    val weeklySalesPyg: Long = 0L,
    val monthlySalesPyg: Long = 0L,
    val totalRevenuePyg: Long = 0L,
    val pendingPaymentCount: Int = 0,
    val activePipelineCount: Int = 0,
    val lowStockCount: Int = 0
)

sealed interface AdminDashboardUiState {
    data object Loading : AdminDashboardUiState
    data class Ready(
        val orders: List<OrderModel>,
        val products: List<ProductModel>,
        val inventory: List<InventoryModel>,
        val auditLogs: List<PaymentAuditLogModel>,
        val settings: BusinessSettingsModel,
        val kpis: AdminSalesKpis
    ) : AdminDashboardUiState
    data class Error(val message: String) : AdminDashboardUiState
}

enum class OrderFilterTab(val label: String) {
    ALL("Todos"),
    PENDING_PAYMENT("Pendientes"),
    CONFIRMED("Confirmados"),
    IN_PROGRESS("En proceso"),
    COMPLETED("Completados"),
    CANCELLED("Cancelados")
}

class AdminViewModel(
    private val repository: PizzaClubRepository,
    val adminUid: String
) : ViewModel() {

    private val _selectedOrderFilter = MutableStateFlow(OrderFilterTab.PENDING_PAYMENT)
    val selectedOrderFilter: StateFlow<OrderFilterTab> = _selectedOrderFilter.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    val systemLogs: StateFlow<List<SystemLogEntry>> = repository.observeSystemLogs()

    fun isCloudConnected(): Boolean = repository.isCloudConnected()

    private val ordersFlow = repository.observeAllOrders()
    private val productsFlow = repository.observeProducts()
    private val inventoryFlow = repository.observeInventory()
    private val auditFlow = repository.observeAuditLogs()
    private val settingsFlow = repository.observeBusinessSettings()

    val dashboardState: StateFlow<AdminDashboardUiState> = combine(
        ordersFlow,
        productsFlow,
        inventoryFlow,
        auditFlow,
        settingsFlow
    ) { orders, products, inventory, logs, settings ->
        val kpis = computeSalesKpis(orders, inventory)
        AdminDashboardUiState.Ready(
            orders = orders,
            products = products,
            inventory = inventory,
            auditLogs = logs,
            settings = settings,
            kpis = kpis
        ) as AdminDashboardUiState
    }.catch { error ->
        if (error is CancellationException) throw error
        Log.w("AdminVM", "Error observing admin dashboard", error)
        emit(AdminDashboardUiState.Error(unwrapBackendMessage(error, "Error al cargar el panel administrativo.")))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = AdminDashboardUiState.Loading
    )

    init {
        viewModelScope.launch {
            repository.seedInitialCatalogIfNeeded().onFailure { error ->
                Log.w("AdminVM", "Seed catalog note: ${error.message}")
            }
        }
    }

    fun setOrderFilter(filter: OrderFilterTab) {
        _selectedOrderFilter.value = filter
    }

    fun clearMessage() {
        _actionMessage.value = null
    }

    fun clearSystemLogs() {
        repository.clearSystemLogs()
    }

    fun runSupabaseDiagnostics() {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.runSupabaseDiagnostics()
            _isBusy.value = false
            res.onSuccess { msg ->
                _actionMessage.value = msg
            }.onFailure { err ->
                _actionMessage.value = err.message ?: "Error al diagnosticar Supabase."
            }
        }
    }

    fun confirmPayment(orderId: String, note: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.confirmOrderPayment(orderId, note)
            _isBusy.value = false
            res.onSuccess {
                _actionMessage.value = "Pago confirmado, stock descontado y WhatsApp desbloqueado."
            }.onFailure { err ->
                _actionMessage.value = err.message ?: "No se pudo confirmar el pago."
            }
        }
    }

    fun advanceStatus(order: OrderModel, nextStatus: OrderStatus) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.advanceOrderStatus(order, nextStatus)
            _isBusy.value = false
            res.onSuccess {
                _actionMessage.value = "Pedido ${order.orderNumber} actualizado a: ${nextStatus.labelEs}."
            }.onFailure { err ->
                _actionMessage.value = unwrapBackendMessage(err, "No se pudo actualizar el estado.")
            }
        }
    }

    fun cancelOrder(order: OrderModel, reason: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.cancelOrder(order, reason)
            _isBusy.value = false
            res.onSuccess {
                _actionMessage.value = "Pedido ${order.orderNumber} cancelado."
            }.onFailure { err ->
                _actionMessage.value = unwrapBackendMessage(err, "No se pudo cancelar el pedido.")
            }
        }
    }

    fun adjustStock(productId: String, productName: String, newStock: Long, threshold: Long) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.adjustInventoryStock(productId, productName, newStock, threshold)
            _isBusy.value = false
            res.onSuccess {
                _actionMessage.value = "Stock de $productName actualizado a $newStock."
            }.onFailure { err ->
                _actionMessage.value = unwrapBackendMessage(err, "No se pudo actualizar el stock.")
            }
        }
    }

    fun saveProduct(
        productId: String,
        name: String,
        description: String,
        pricePyg: Long,
        active: Boolean,
        imageKey: String,
        sortOrder: Long,
        isNew: Boolean
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.saveProduct(productId, name, description, pricePyg, active, imageKey, sortOrder, isNew)
            _isBusy.value = false
            res.onSuccess {
                _actionMessage.value = if (isNew) "Sabor '$name' agregado al catálogo." else "Producto '$name' actualizado."
            }.onFailure { err ->
                _actionMessage.value = unwrapBackendMessage(err, "No se pudo guardar el producto.")
            }
        }
    }

    fun saveBusinessSettings(settings: BusinessSettingsModel) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.updateBusinessSettings(settings)
            _isBusy.value = false
            res.onSuccess {
                _actionMessage.value = "Configuración comercial guardada."
            }.onFailure { err ->
                _actionMessage.value = unwrapBackendMessage(err, "No se pudo guardar la configuración.")
            }
        }
    }

    fun buildOrdersCsv(orders: List<OrderModel>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("America/Asuncion")
        }
        val sb = StringBuilder()
        sb.appendLine("OrderNumber,Status,CustomerName,CustomerPhone,PreparationMode,FulfillmentType,Address,Items,TotalPYG,PaymentAlias,CreatedAt")
        for (o in orders) {
            val address = if (o.fulfillmentType == "DELIVERY") {
                "${o.neighborhood.orEmpty()} - ${o.streetAndNumber.orEmpty()}"
            } else {
                "Retiro ${o.pickupDate.orEmpty()} ${o.pickupTime.orEmpty()}"
            }
            val itemsSummary = o.items.joinToString(" | ") { "${it.quantity}x ${it.productName}" }
            val createdStr = o.createdAt?.toDate()?.let { dateFormat.format(it) } ?: ""
            sb.appendLine(
                listOf(
                    o.orderNumber,
                    o.status,
                    o.customerName,
                    o.customerPhone,
                    o.preparationMode,
                    o.fulfillmentType,
                    address,
                    itemsSummary,
                    o.totalAmountPyg.toString(),
                    o.paymentAlias,
                    createdStr
                ).joinToString(",") { cell -> "\"${cell.replace("\"", "\"\"")}\"" }
            )
        }
        return sb.toString()
    }

    private fun computeSalesKpis(orders: List<OrderModel>, inventory: List<InventoryModel>): AdminSalesKpis {
        val tz = TimeZone.getTimeZone("America/Asuncion")
        val nowCal = Calendar.getInstance(tz)
        val startOfDay = (nowCal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val startOfWeek = (nowCal.clone() as Calendar).apply {
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val startOfMonth = (nowCal.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val paidStatuses = setOf(
            OrderStatus.PAYMENT_CONFIRMED.rawValue,
            OrderStatus.PREPARING.rawValue,
            OrderStatus.READY_FOR_PICKUP.rawValue,
            OrderStatus.OUT_FOR_DELIVERY.rawValue,
            OrderStatus.COMPLETED.rawValue
        )

        var daily = 0L
        var weekly = 0L
        var monthly = 0L
        var total = 0L
        var pendingCount = 0
        var activeCount = 0

        for (order in orders) {
            when (order.status) {
                OrderStatus.PENDING_PAYMENT.rawValue -> pendingCount++
                OrderStatus.PAYMENT_CONFIRMED.rawValue,
                OrderStatus.PREPARING.rawValue,
                OrderStatus.READY_FOR_PICKUP.rawValue,
                OrderStatus.OUT_FOR_DELIVERY.rawValue -> activeCount++
            }
            if (order.status in paidStatuses) {
                total += order.totalAmountPyg
                val tsMillis = (order.paymentConfirmedAt ?: order.createdAt)?.toDate()?.time ?: 0L
                if (tsMillis >= startOfDay) daily += order.totalAmountPyg
                if (tsMillis >= startOfWeek) weekly += order.totalAmountPyg
                if (tsMillis >= startOfMonth) monthly += order.totalAmountPyg
            }
        }

        val effectiveInv = if (inventory.isEmpty()) DefaultCatalogDefaults.defaultInventory else inventory
        val lowStockCount = effectiveInv.count { it.stock <= it.lowStockThreshold }

        return AdminSalesKpis(
            dailySalesPyg = daily,
            weeklySalesPyg = weekly,
            monthlySalesPyg = monthly,
            totalRevenuePyg = total,
            pendingPaymentCount = pendingCount,
            activePipelineCount = activeCount,
            lowStockCount = lowStockCount
        )
    }
}
