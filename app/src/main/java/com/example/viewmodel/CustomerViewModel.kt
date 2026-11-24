package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BusinessSettingsModel
import com.example.data.DefaultCatalogDefaults
import com.example.data.FulfillmentType
import com.example.data.InventoryModel
import com.example.data.LocalOrderStore
import com.example.data.LocalSavedOrder
import com.example.data.OrderLineItem
import com.example.data.OrderStatusTrackerModel
import com.example.data.PizzaClubRepository
import com.example.data.PreparationMode
import com.example.data.ProductModel
import com.example.data.unwrapFirestoreMessage
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

data class CatalogItemUi(
    val product: ProductModel,
    val stock: Long,
    val lowStockThreshold: Long,
    val cartQuantity: Int
)

data class CheckoutFormState(
    val preparationMode: PreparationMode = PreparationMode.READY_TO_EAT,
    val fulfillmentType: FulfillmentType = FulfillmentType.DELIVERY,
    val customerName: String = "",
    val customerPhone: String = "",
    val desiredDate: String = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()),
    val additionalInstructions: String = "",
    val neighborhood: String = "",
    val streetAndNumber: String = "",
    val deliveryReference: String = "",
    val googleMapsLink: String = "",
    val pickupDate: String = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()),
    val pickupTime: String = "19:30"
)

sealed interface CatalogUiState {
    data object Loading : CatalogUiState
    data class Ready(
        val items: List<CatalogItemUi>,
        val settings: BusinessSettingsModel,
        val totalPizzas: Int,
        val subtotalPyg: Long
    ) : CatalogUiState
    data class Error(val message: String) : CatalogUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class CustomerViewModel(
    private val repository: PizzaClubRepository,
    private val localOrderStore: LocalOrderStore
) : ViewModel() {

    private val _cartQuantities = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val _checkoutForm = MutableStateFlow(CheckoutFormState())
    val checkoutForm: StateFlow<CheckoutFormState> = _checkoutForm.asStateFlow()

    private val _isSubmittingOrder = MutableStateFlow(false)
    val isSubmittingOrder: StateFlow<Boolean> = _isSubmittingOrder.asStateFlow()

    private val _submitError = MutableStateFlow<String?>(null)
    val submitError: StateFlow<String?> = _submitError.asStateFlow()

    private val _selectedOrderId = MutableStateFlow<String?>(null)
    val selectedOrderId: StateFlow<String?> = _selectedOrderId.asStateFlow()

    private val _isRefreshingHistory = MutableStateFlow(false)
    val isRefreshingHistory: StateFlow<Boolean> = _isRefreshingHistory.asStateFlow()

    private var hasSyncedInitialHistory = false

    val savedOrders: StateFlow<List<LocalSavedOrder>> = localOrderStore.savedOrdersFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    // Real-time tracker for the currently viewed customer order (updates automatically when Admin confirms payment)
    val liveTrackedOrderStatus: StateFlow<OrderStatusTrackerModel?> = _selectedOrderId
        .flatMapLatest { orderId ->
            if (orderId.isNullOrBlank()) {
                flowOf(null)
            } else {
                repository.observeOrderStatusTracker(orderId)
                    .catch { error ->
                        val fsError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                        if (fsError == null && error is CancellationException) throw error
                        Log.w("CustomerVM", "Error observing order status $orderId", fsError ?: error)
                        emit(null)
                    }
            }
        }
        .onEach { tracker ->
            if (tracker != null) {
                localOrderStore.updateOrderStatus(tracker.id, tracker.status, tracker.whatsappUnlocked)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    private val productsFlow = repository.observeProducts()
        .catch { error ->
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fsError == null && error is CancellationException) throw error
            Log.w("CustomerVM", "Using fallback products due to stream error", fsError ?: error)
            emit(DefaultCatalogDefaults.defaultProducts)
        }

    private val inventoryFlow = repository.observeInventory()
        .catch { error ->
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fsError == null && error is CancellationException) throw error
            Log.w("CustomerVM", "Using fallback inventory due to stream error", fsError ?: error)
            emit(DefaultCatalogDefaults.defaultInventory)
        }

    private val settingsFlow = repository.observeBusinessSettings()
        .catch { error ->
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fsError == null && error is CancellationException) throw error
            Log.w("CustomerVM", "Using fallback settings due to stream error", fsError ?: error)
            emit(DefaultCatalogDefaults.defaultSettings)
        }

    val catalogState: StateFlow<CatalogUiState> = combine(
        productsFlow,
        inventoryFlow,
        settingsFlow,
        _cartQuantities
    ) { remoteProducts, remoteInventory, settings, cart ->
        val products = if (remoteProducts.isEmpty()) DefaultCatalogDefaults.defaultProducts else remoteProducts
        val invMap: Map<String, InventoryModel> = if (remoteInventory.isEmpty()) {
            DefaultCatalogDefaults.defaultInventory.associateBy { it.id }
        } else {
            remoteInventory.associateBy { it.id }
        }

        val uiItems = CartStateCalculator.buildCatalogItems(products, invMap, cart)
        val totalPizzas = CartStateCalculator.computeTotalCount(uiItems)
        val subtotal = CartStateCalculator.computeSubtotalPyg(uiItems)

        CatalogUiState.Ready(
            items = uiItems,
            settings = settings,
            totalPizzas = totalPizzas,
            subtotalPyg = subtotal
        ) as CatalogUiState
    }.catch { error ->
        val fsError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>().firstOrNull()
        if (fsError == null && error is CancellationException) throw error
        emit(CatalogUiState.Error(unwrapFirestoreMessage(error, "Error al cargar el catálogo")))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = CatalogUiState.Loading
    )

    init {
        // Sync active order status and refresh full local order history once on load
        savedOrders.onEach { list ->
            if (_selectedOrderId.value == null && list.isNotEmpty()) {
                _selectedOrderId.value = list.first().orderId
            }
            if (!hasSyncedInitialHistory && list.isNotEmpty()) {
                hasSyncedInitialHistory = true
                refreshAllSavedOrdersStatus(list)
            }
        }.launchIn(viewModelScope)
    }

    fun incrementQuantity(productId: String, availableStock: Long) {
        _cartQuantities.update { current ->
            CartStateCalculator.increment(current, productId, availableStock)
        }
    }

    fun decrementQuantity(productId: String) {
        _cartQuantities.update { current ->
            CartStateCalculator.decrement(current, productId)
        }
    }

    fun updateCheckoutForm(transform: (CheckoutFormState) -> CheckoutFormState) {
        _checkoutForm.update(transform)
        _submitError.value = null
    }

    fun selectOrder(orderId: String) {
        _selectedOrderId.value = orderId
    }

    fun refreshAllSavedOrdersStatus(ordersOverride: List<LocalSavedOrder>? = null) {
        val orders = ordersOverride ?: savedOrders.value
        if (orders.isEmpty()) return
        viewModelScope.launch {
            _isRefreshingHistory.value = true
            try {
                for (saved in orders) {
                    val tracker = repository.fetchOrderStatusOnce(saved.orderId)
                    if (tracker != null) {
                        localOrderStore.updateOrderStatus(tracker.id, tracker.status, tracker.whatsappUnlocked)
                    }
                }
            } finally {
                _isRefreshingHistory.value = false
            }
        }
    }

    fun clearSubmitError() {
        _submitError.value = null
    }

    fun submitPreOrder(onOrderCreated: (String) -> Unit) {
        val state = catalogState.value as? CatalogUiState.Ready ?: return
        val form = _checkoutForm.value
        val lineItems = state.items
            .filter { it.cartQuantity > 0 }
            .map { item ->
                OrderLineItem(
                    productId = item.product.id,
                    productName = item.product.name,
                    unitPricePyg = item.product.pricePyg,
                    quantity = item.cartQuantity.toLong(),
                    lineTotalPyg = item.product.pricePyg * item.cartQuantity.toLong()
                )
            }

        if (lineItems.isEmpty()) {
            _submitError.value = "Agregá al menos una pizza antes de continuar."
            return
        }
        // Double check stock limits
        for (item in state.items) {
            if (item.cartQuantity > item.stock) {
                _submitError.value = "La cantidad de ${item.product.name} supera el stock disponible (${item.stock})."
                return
            }
        }

        viewModelScope.launch {
            _isSubmittingOrder.value = true
            _submitError.value = null
            val result = repository.submitPreOrder(
                customerName = form.customerName,
                customerPhone = form.customerPhone,
                desiredDate = form.desiredDate,
                additionalInstructions = form.additionalInstructions,
                preparationMode = form.preparationMode,
                fulfillmentType = form.fulfillmentType,
                neighborhood = form.neighborhood,
                streetAndNumber = form.streetAndNumber,
                deliveryReference = form.deliveryReference,
                googleMapsLink = form.googleMapsLink,
                pickupDate = form.pickupDate,
                pickupTime = form.pickupTime,
                items = lineItems,
                settings = state.settings
            )
            _isSubmittingOrder.value = false
            result.onSuccess { savedOrder ->
                localOrderStore.saveOrder(savedOrder)
                _cartQuantities.value = emptyMap()
                _selectedOrderId.value = savedOrder.orderId
                onOrderCreated(savedOrder.orderId)
            }.onFailure { error ->
                _submitError.value = unwrapFirestoreMessage(error, "No se pudo registrar la preorden.")
            }
        }
    }
}
