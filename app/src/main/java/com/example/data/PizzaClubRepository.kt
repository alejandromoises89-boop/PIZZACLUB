package com.example.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

class PizzaClubRepository(
    context: Context
) {
    private val appContext = context.applicationContext
    private val db = PizzaClubDatabase.getInstance(appContext)
    private val dao = db.pizzaClubDao()
    val cloudClient = SupabaseRestClient(appContext)
    val systemLogStore = SystemLogStore.getInstance(appContext)

    fun isCloudConnected(): Boolean = cloudClient.isConfigured()

    fun observeSystemLogs() = systemLogStore.logs

    fun clearSystemLogs() = systemLogStore.clear()

    private suspend fun ensureLocalSeeded() {
        val now = System.currentTimeMillis()
        if (dao.getProductsCount().isEmpty()) {
            dao.upsertProducts(
                DefaultCatalogDefaults.defaultProducts.map {
                    ProductEntity(
                        id = it.id,
                        name = it.name,
                        description = it.description,
                        pricePyg = it.pricePyg,
                        active = it.active,
                        imageKey = it.imageKey,
                        sortOrder = it.sortOrder,
                        createdAtMillis = now,
                        updatedAtMillis = now
                    )
                }
            )
            dao.upsertInventory(
                DefaultCatalogDefaults.defaultInventory.map {
                    InventoryEntity(
                        id = it.id,
                        productName = it.productName,
                        stock = it.stock,
                        lowStockThreshold = it.lowStockThreshold,
                        updatedBy = "system",
                        updatedAtMillis = now
                    )
                }
            )
        }
        if (dao.getSettingsOnce() == null) {
            val s = DefaultCatalogDefaults.defaultSettings
            dao.upsertSettings(
                BusinessSettingsEntity(
                    id = s.id,
                    brandName = s.brandName,
                    instagramHandle = s.instagramHandle,
                    whatsappNumber = s.whatsappNumber,
                    bankName = s.bankName,
                    paymentAlias = s.paymentAlias,
                    deliveryCity = s.deliveryCity,
                    currency = s.currency,
                    acceptingOrders = s.acceptingOrders,
                    treasureCode = s.treasureCode,
                    treasureReward = s.treasureReward,
                    googleReviewUrl = s.googleReviewUrl,
                    pwaInstallUrl = s.pwaInstallUrl,
                    updatedAtMillis = now
                )
            )
        }
    }

    private fun periodicSyncTick(
        intervalMs: Long = 5_000L,
        source: String,
        action: suspend () -> Unit
    ): Flow<Unit> = flow {
        ensureLocalSeeded()
        var lastErrorSignature: String? = null
        while (currentCoroutineContext().isActive) {
            if (cloudClient.isConfigured()) {
                try {
                    action()
                    lastErrorSignature = null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("PizzaClubRepo", "Cloud sync poll note ($source): ${e.message}")
                    val signature = "$source:${e.javaClass.simpleName}:${e.message?.take(80)}"
                    if (signature != lastErrorSignature) {
                        lastErrorSignature = signature
                        systemLogStore.logError(
                            source = source,
                            message = "Error durante $source en Supabase",
                            throwable = e
                        )
                    }
                }
            }
            emit(Unit)
            delay(intervalMs)
        }
    }

    // --- Public Customer Streams ---

    fun observeProducts(): Flow<List<ProductModel>> {
        val localFlow = dao.observeProducts().map { list ->
            if (list.isEmpty()) DefaultCatalogDefaults.defaultProducts else list.map { it.toModel() }
        }
        val syncFlow = periodicSyncTick(12_000L, source = "Carga de Productos") {
            val remote = cloudClient.fetchProducts()
            if (remote.isNotEmpty()) {
                dao.upsertProducts(remote)
            } else {
                systemLogStore.logWarn(
                    source = "Carga de Productos",
                    message = "La tabla products en Supabase devolvió 0 registros; usando catálogo local."
                )
            }
        }
        return merge(localFlow, flow {
            syncFlow.collect { }
        })
    }

    fun observeInventory(): Flow<List<InventoryModel>> {
        val localFlow = dao.observeInventory().map { list ->
            if (list.isEmpty()) DefaultCatalogDefaults.defaultInventory else list.map { it.toModel() }
        }
        val syncFlow = periodicSyncTick(8_000L, source = "Sincronización Stock") {
            val remote = cloudClient.fetchInventory()
            if (remote.isNotEmpty()) dao.upsertInventory(remote)
        }
        return merge(localFlow, flow {
            syncFlow.collect { }
        })
    }

    fun observeBusinessSettings(): Flow<BusinessSettingsModel> {
        val localFlow = dao.observeSettings().map { entity ->
            entity?.toModel() ?: DefaultCatalogDefaults.defaultSettings
        }
        val syncFlow = periodicSyncTick(15_000L, source = "Sincronización Ajustes") {
            val remote = cloudClient.fetchBusinessSettings()
            if (remote != null) dao.upsertSettings(remote)
        }
        return merge(localFlow, flow {
            syncFlow.collect { }
        })
    }

    fun observeOrderStatusTracker(orderId: String): Flow<OrderStatusTrackerModel?> {
        if (orderId.length < 10) return flow { emit(null) }
        val localFlow = dao.observeOrderById(orderId).map { it?.toTrackerModel() }
        val syncFlow = periodicSyncTick(5_000L, source = "Seguimiento Pedido") {
            val remote = cloudClient.fetchOrderStatusOnce(orderId)
            if (remote != null) {
                val existing = dao.getOrderById(orderId)
                if (existing != null) {
                    dao.upsertOrder(
                        existing.copy(
                            status = remote.status,
                            whatsappUnlocked = remote.whatsappUnlocked,
                            updatedAtMillis = remote.updatedAt?.millis ?: System.currentTimeMillis()
                        )
                    )
                }
            }
        }
        return merge(localFlow, flow {
            syncFlow.collect { }
        })
    }

    suspend fun fetchOrderStatusOnce(orderId: String): OrderStatusTrackerModel? {
        if (orderId.length < 10) return null
        return try {
            if (cloudClient.isConfigured()) {
                val remote = cloudClient.fetchOrderStatusOnce(orderId)
                if (remote != null) {
                    val existing = dao.getOrderById(orderId)
                    if (existing != null) {
                        dao.upsertOrder(
                            existing.copy(
                                status = remote.status,
                                whatsappUnlocked = remote.whatsappUnlocked,
                                updatedAtMillis = remote.updatedAt?.millis ?: System.currentTimeMillis()
                            )
                        )
                    }
                    return remote
                }
            }
            dao.getOrderById(orderId)?.toTrackerModel()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("PizzaClubRepo", "Error fetching status $orderId: ${e.message}")
            systemLogStore.logError(
                source = "Seguimiento Pedido",
                message = "No se pudo consultar el estado de $orderId en Supabase",
                throwable = e
            )
            dao.getOrderById(orderId)?.toTrackerModel()
        }
    }

    // --- Customer Pre-Order Submission (No Login Required) ---

    suspend fun submitPreOrder(
        customerName: String,
        customerPhone: String,
        desiredDate: String,
        additionalInstructions: String,
        preparationMode: PreparationMode,
        fulfillmentType: FulfillmentType,
        neighborhood: String,
        streetAndNumber: String,
        deliveryReference: String,
        googleMapsLink: String,
        pickupDate: String,
        pickupTime: String,
        items: List<OrderLineItem>,
        settings: BusinessSettingsModel
    ): Result<LocalSavedOrder> {
        val cleanName = customerName.trim()
        val cleanPhone = customerPhone.trim()
        val cleanDate = desiredDate.trim()
        val orderId = "ord_${UUID.randomUUID().toString().replace("-", "").take(24)}"

        return try {
            require(cleanName.length in 2..100) { "Ingresá tu nombre completo (mínimo 2 caracteres)." }
            require(cleanPhone.length in 6..30) { "Ingresá un teléfono de contacto válido." }
            require(cleanDate.isNotBlank() && cleanDate.length <= 40) { "Indicá la fecha deseada." }
            require(items.isNotEmpty() && items.size <= 20) { "Seleccioná al menos una pizza." }
            if (fulfillmentType == FulfillmentType.DELIVERY) {
                require(neighborhood.trim().isNotBlank()) { "Ingresá el barrio para el delivery." }
                require(streetAndNumber.trim().isNotBlank()) { "Ingresá la calle y número de casa." }
            } else {
                require(pickupDate.trim().isNotBlank()) { "Indicá la fecha de retiro." }
                require(pickupTime.trim().isNotBlank()) { "Indicá el horario aproximado de retiro." }
            }

            val subtotal = items.sumOf { it.lineTotalPyg }
            require(subtotal > 0L) { "El total del pedido debe ser mayor a cero." }

            val dateCode = SimpleDateFormat("yyMMdd", Locale.US).format(Date())
            val randomSuffix = Random.nextInt(1000, 9999)
            val orderNumber = "PC-$dateCode-$randomSuffix"
            val nowMillis = System.currentTimeMillis()

            val orderEntity = OrderEntity(
                id = orderId,
                orderNumber = orderNumber,
                customerName = cleanName,
                customerPhone = cleanPhone,
                desiredDate = cleanDate,
                additionalInstructions = additionalInstructions.trim().take(400).ifBlank { null },
                preparationMode = preparationMode.label,
                fulfillmentType = fulfillmentType.rawValue,
                neighborhood = neighborhood.trim().take(100).ifBlank { null },
                streetAndNumber = streetAndNumber.trim().take(150).ifBlank { null },
                deliveryReference = deliveryReference.trim().take(200).ifBlank { null },
                googleMapsLink = googleMapsLink.trim().take(300).ifBlank { null },
                pickupDate = pickupDate.trim().take(40).ifBlank { null },
                pickupTime = pickupTime.trim().take(20).ifBlank { null },
                itemsJson = encodeOrderItemsJson(items),
                subtotalPyg = subtotal,
                totalAmountPyg = subtotal,
                status = OrderStatus.PENDING_PAYMENT.rawValue,
                whatsappUnlocked = false,
                paymentMethod = "BANK_TRANSFER",
                paymentBank = settings.bankName,
                paymentAlias = settings.paymentAlias,
                paymentConfirmedAtMillis = null,
                confirmedByAdminId = null,
                confirmedByAdminEmail = null,
                cancellationReason = null,
                createdAtMillis = nowMillis,
                updatedAtMillis = nowMillis
            )

            ensureLocalSeeded()
            dao.upsertOrder(orderEntity)
            if (cloudClient.isConfigured()) {
                cloudClient.insertPreOrder(orderEntity)
            }

            Result.success(
                LocalSavedOrder(
                    orderId = orderId,
                    orderNumber = orderNumber,
                    customerName = cleanName,
                    customerPhone = cleanPhone,
                    desiredDate = cleanDate,
                    additionalInstructions = additionalInstructions.trim(),
                    preparationMode = preparationMode.label,
                    fulfillmentType = fulfillmentType.rawValue,
                    neighborhood = neighborhood.trim(),
                    streetAndNumber = streetAndNumber.trim(),
                    deliveryReference = deliveryReference.trim(),
                    googleMapsLink = googleMapsLink.trim(),
                    pickupDate = pickupDate.trim(),
                    pickupTime = pickupTime.trim(),
                    items = items,
                    totalAmountPyg = subtotal,
                    paymentBank = settings.bankName,
                    paymentAlias = settings.paymentAlias,
                    whatsappNumber = settings.whatsappNumber,
                    status = OrderStatus.PENDING_PAYMENT.rawValue,
                    whatsappUnlocked = false,
                    createdAtMillis = nowMillis
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            systemLogStore.logError(
                source = "Envío de Preorden",
                message = "Error al guardar o sincronizar el pedido $orderId",
                throwable = e
            )
            Result.failure(e)
        }
    }

    // --- Admin Streams ---

    fun observeAllOrders(): Flow<List<OrderModel>> {
        val localFlow = dao.observeOrders().map { list -> list.map { it.toModel() } }
        val syncFlow = periodicSyncTick(5_000L, source = "Sincronización Pedidos") {
            val remote = cloudClient.fetchAllOrders()
            if (remote.isNotEmpty()) dao.upsertOrders(remote)
        }
        return merge(localFlow, flow {
            syncFlow.collect { }
        })
    }

    fun observeAuditLogs(): Flow<List<PaymentAuditLogModel>> {
        val localFlow = dao.observeAuditLogs().map { list -> list.map { it.toModel() } }
        val syncFlow = periodicSyncTick(8_000L, source = "Sincronización Auditoría") {
            val remote = cloudClient.fetchAuditLogs()
            if (remote.isNotEmpty()) dao.upsertAuditLogs(remote)
        }
        return merge(localFlow, flow {
            syncFlow.collect { }
        })
    }

    // --- Admin Operations ---

    suspend fun runSupabaseDiagnostics(): Result<String> {
        if (!cloudClient.isConfigured()) {
            val msg = "Base online no configurada: completá URL y Anon Key de Supabase en Ajustes."
            systemLogStore.logWarn("Diagnóstico Supabase", msg)
            return Result.success(msg)
        }
        val failures = mutableListOf<String>()
        var productCount = 0
        var orderCount = 0
        runCatching { cloudClient.fetchProducts() }
            .onSuccess { productCount = it.size }
            .onFailure {
                failures.add("products")
                systemLogStore.logError("Diagnóstico Productos", "Fallo al leer tabla products en Supabase", it)
            }
        runCatching { cloudClient.fetchInventory() }
            .onFailure {
                failures.add("inventory")
                systemLogStore.logError("Diagnóstico Stock", "Fallo al leer tabla inventory en Supabase", it)
            }
        runCatching { cloudClient.fetchAllOrders() }
            .onSuccess { orderCount = it.size }
            .onFailure {
                failures.add("orders")
                systemLogStore.logError("Diagnóstico Pedidos", "Fallo al leer tabla orders en Supabase", it)
            }
        runCatching { cloudClient.fetchBusinessSettings() }
            .onFailure {
                failures.add("business_settings")
                systemLogStore.logError("Diagnóstico Ajustes", "Fallo al leer tabla business_settings en Supabase", it)
            }

        return if (failures.isEmpty()) {
            val okMsg = "Conexión OK ($productCount productos, $orderCount pedidos sincronizados)."
            systemLogStore.logInfo("Diagnóstico Supabase", okMsg)
            Result.success(okMsg)
        } else {
            val errMsg = "Fallaron las tablas: ${failures.joinToString(", ")}. Revisá el detalle abajo."
            Result.failure(IllegalStateException(errMsg))
        }
    }

    suspend fun seedInitialCatalogIfNeeded(): Result<Unit> {
        return try {
            ensureLocalSeeded()
            if (cloudClient.isConfigured()) {
                val remoteProducts = cloudClient.fetchProducts()
                if (remoteProducts.isNotEmpty()) {
                    dao.upsertProducts(remoteProducts)
                }
                val remoteInv = cloudClient.fetchInventory()
                if (remoteInv.isNotEmpty()) {
                    dao.upsertInventory(remoteInv)
                }
                cloudClient.fetchBusinessSettings()?.let { dao.upsertSettings(it) }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            systemLogStore.logError(
                source = "Carga Inicial Catálogo",
                message = "Error al cargar catálogo/inventario desde Supabase",
                throwable = e
            )
            Result.failure(e)
        }
    }

    suspend fun confirmOrderPayment(orderId: String, note: String = "Depósito Itaú verificado manualmente"): Result<Unit> {
        return try {
            require(orderId.isNotBlank()) { "ID de pedido inválido." }
            val now = System.currentTimeMillis()
            val adminId = "admin_local"
            val adminEmail = DefaultCatalogDefaults.BOOTSTRAP_ADMIN_EMAIL

            dao.confirmOrderPaymentLocally(orderId, adminId, adminEmail, note, now)
            val updatedOrder = dao.getOrderById(orderId)

            if (cloudClient.isConfigured() && updatedOrder != null) {
                for (item in decodeOrderItemsJson(updatedOrder.itemsJson)) {
                    dao.getInventoryById(item.productId)?.let { cloudClient.upsertInventoryItem(it) }
                }
                cloudClient.patchOrderAndStatus(
                    orderId = orderId,
                    orderPatch = JSONObject().apply {
                        put("status", OrderStatus.PAYMENT_CONFIRMED.rawValue)
                        put("whatsapp_unlocked", true)
                        put("payment_confirmed_at_ms", now)
                        put("confirmed_by_admin_id", adminId)
                        put("confirmed_by_admin_email", adminEmail)
                        put("updated_at_ms", now)
                    },
                    statusPatch = JSONObject().apply {
                        put("status", OrderStatus.PAYMENT_CONFIRMED.rawValue)
                        put("whatsapp_unlocked", true)
                        put("updated_at_ms", now)
                    }
                )
                cloudClient.insertAuditLog(
                    AuditLogEntity(
                        id = "log_${UUID.randomUUID().toString().replace("-", "").take(20)}",
                        orderId = orderId,
                        orderNumber = updatedOrder.orderNumber,
                        adminId = adminId,
                        adminEmail = adminEmail,
                        action = "CONFIRM_PAYMENT",
                        previousStatus = OrderStatus.PENDING_PAYMENT.rawValue,
                        newStatus = OrderStatus.PAYMENT_CONFIRMED.rawValue,
                        amountPyg = updatedOrder.totalAmountPyg,
                        note = note.take(300),
                        createdAtMillis = now
                    )
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            systemLogStore.logError("Admin Confirmar Pago", "Error al sincronizar confirmación de $orderId", e)
            Result.failure(e)
        }
    }

    suspend fun advanceOrderStatus(order: OrderModel, nextStatus: OrderStatus): Result<Unit> {
        return try {
            require(nextStatus != OrderStatus.PENDING_PAYMENT && nextStatus != OrderStatus.CANCELLED)
            val now = System.currentTimeMillis()
            val existing = dao.getOrderById(order.id)
            if (existing != null) {
                dao.upsertOrder(
                    existing.copy(
                        status = nextStatus.rawValue,
                        whatsappUnlocked = true,
                        updatedAtMillis = now
                    )
                )
            }
            val log = AuditLogEntity(
                id = "log_${UUID.randomUUID().toString().replace("-", "").take(20)}",
                orderId = order.id,
                orderNumber = order.orderNumber,
                adminId = "admin_local",
                adminEmail = DefaultCatalogDefaults.BOOTSTRAP_ADMIN_EMAIL,
                action = "STATUS_CHANGE",
                previousStatus = order.status,
                newStatus = nextStatus.rawValue,
                amountPyg = order.totalAmountPyg,
                note = "Estado cambiado a ${nextStatus.labelEs}",
                createdAtMillis = now
            )
            dao.insertAuditLog(log)

            if (cloudClient.isConfigured()) {
                cloudClient.patchOrderAndStatus(
                    orderId = order.id,
                    orderPatch = JSONObject().apply {
                        put("status", nextStatus.rawValue)
                        put("updated_at_ms", now)
                    },
                    statusPatch = JSONObject().apply {
                        put("status", nextStatus.rawValue)
                        put("whatsapp_unlocked", true)
                        put("updated_at_ms", now)
                    }
                )
                cloudClient.insertAuditLog(log)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cancelOrder(order: OrderModel, reason: String): Result<Unit> {
        return try {
            val cleanReason = reason.trim().ifBlank { "Cancelado por el administrador" }.take(200)
            val now = System.currentTimeMillis()
            val existing = dao.getOrderById(order.id)
            if (existing != null) {
                dao.upsertOrder(
                    existing.copy(
                        status = OrderStatus.CANCELLED.rawValue,
                        whatsappUnlocked = false,
                        cancellationReason = cleanReason,
                        updatedAtMillis = now
                    )
                )
            }
            val log = AuditLogEntity(
                id = "log_${UUID.randomUUID().toString().replace("-", "").take(20)}",
                orderId = order.id,
                orderNumber = order.orderNumber,
                adminId = "admin_local",
                adminEmail = DefaultCatalogDefaults.BOOTSTRAP_ADMIN_EMAIL,
                action = "CANCEL_ORDER",
                previousStatus = order.status,
                newStatus = OrderStatus.CANCELLED.rawValue,
                amountPyg = order.totalAmountPyg,
                note = cleanReason,
                createdAtMillis = now
            )
            dao.insertAuditLog(log)

            if (cloudClient.isConfigured()) {
                cloudClient.patchOrderAndStatus(
                    orderId = order.id,
                    orderPatch = JSONObject().apply {
                        put("status", OrderStatus.CANCELLED.rawValue)
                        put("whatsapp_unlocked", false)
                        put("cancellation_reason", cleanReason)
                        put("updated_at_ms", now)
                    },
                    statusPatch = JSONObject().apply {
                        put("status", OrderStatus.CANCELLED.rawValue)
                        put("whatsapp_unlocked", false)
                        put("updated_at_ms", now)
                    }
                )
                cloudClient.insertAuditLog(log)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun adjustInventoryStock(productId: String, productName: String, newStock: Long, lowThreshold: Long): Result<Unit> {
        return try {
            require(newStock >= 0L) { "El stock no puede ser negativo." }
            require(lowThreshold >= 0L) { "La alerta mínima no puede ser negativa." }
            val now = System.currentTimeMillis()
            val inv = InventoryEntity(
                id = productId,
                productName = productName,
                stock = newStock,
                lowStockThreshold = lowThreshold,
                updatedBy = "admin_local",
                updatedAtMillis = now
            )
            dao.upsertInventoryItem(inv)
            val log = AuditLogEntity(
                id = "log_${UUID.randomUUID().toString().replace("-", "").take(20)}",
                orderId = productId,
                orderNumber = "STOCK-$productId".take(32),
                adminId = "admin_local",
                adminEmail = DefaultCatalogDefaults.BOOTSTRAP_ADMIN_EMAIL,
                action = "STOCK_ADJUSTMENT",
                previousStatus = productName,
                newStatus = "STOCK_$newStock",
                amountPyg = 0L,
                note = "Stock ajustado a $newStock unidades ($productName)",
                createdAtMillis = now
            )
            dao.insertAuditLog(log)

            if (cloudClient.isConfigured()) {
                cloudClient.upsertInventoryItem(inv)
                cloudClient.insertAuditLog(log)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveProduct(
        productId: String,
        name: String,
        description: String,
        pricePyg: Long,
        active: Boolean,
        imageKey: String,
        sortOrder: Long,
        isNew: Boolean
    ): Result<Unit> {
        return try {
            require(productId.matches(Regex("^[a-zA-Z0-9_\\-]+$"))) { "ID de producto inválido." }
            require(name.trim().length in 1..80) { "Nombre de pizza inválido." }
            require(pricePyg in 1..10_000_000) { "Precio debe ser mayor a 0 Gs." }
            val now = System.currentTimeMillis()

            val product = ProductEntity(
                id = productId,
                name = name.trim(),
                description = description.trim().take(300),
                pricePyg = pricePyg,
                active = active,
                imageKey = imageKey,
                sortOrder = sortOrder,
                createdAtMillis = now,
                updatedAtMillis = now
            )
            dao.upsertProduct(product)
            if (cloudClient.isConfigured()) {
                cloudClient.upsertProduct(product)
            }
            if (isNew) {
                val inv = InventoryEntity(
                    id = productId,
                    productName = name.trim(),
                    stock = DefaultCatalogDefaults.INITIAL_STOCK,
                    lowStockThreshold = 5L,
                    updatedBy = "admin_local",
                    updatedAtMillis = now
                )
                dao.upsertInventoryItem(inv)
                if (cloudClient.isConfigured()) {
                    cloudClient.upsertInventoryItem(inv)
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateBusinessSettings(settings: BusinessSettingsModel): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()
            val entity = BusinessSettingsEntity(
                id = "default",
                brandName = settings.brandName.trim().take(80),
                instagramHandle = settings.instagramHandle.trim().take(60),
                whatsappNumber = settings.whatsappNumber.trim().take(20),
                bankName = settings.bankName.trim().take(80),
                paymentAlias = settings.paymentAlias.trim().take(120),
                deliveryCity = settings.deliveryCity.trim().take(80),
                currency = "PYG",
                acceptingOrders = settings.acceptingOrders,
                treasureCode = settings.treasureCode.trim().uppercase().take(40).ifBlank { "PIZZACLUB-ORO" },
                treasureReward = settings.treasureReward.trim().take(160).ifBlank { "Dip Artesanal + 10% OFF en tu próxima pizza" },
                googleReviewUrl = settings.googleReviewUrl.trim().take(300)
                    .ifBlank { DefaultCatalogDefaults.defaultSettings.googleReviewUrl },
                pwaInstallUrl = settings.pwaInstallUrl.trim().take(300)
                    .ifBlank { DefaultCatalogDefaults.defaultSettings.pwaInstallUrl },
                updatedAtMillis = now
            )
            dao.upsertSettings(entity)
            if (cloudClient.isConfigured()) {
                cloudClient.upsertBusinessSettings(entity)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            systemLogStore.logError("Admin Ajustes", "Error al guardar configuración comercial en Supabase", e)
            Result.failure(e)
        }
    }
}
