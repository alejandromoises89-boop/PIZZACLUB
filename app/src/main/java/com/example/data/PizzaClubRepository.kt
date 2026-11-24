package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

class PizzaClubRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireAdminIdentity(): Pair<String, String> {
        val user = auth.currentUser
            ?: throw IllegalStateException("Debés iniciar sesión con Google para operar como administrador.")
        val email = user.email ?: ""
        return user.uid to email
    }

    suspend fun verifyIsAdmin(): Boolean {
        val user = auth.currentUser ?: return false
        if (user.isEmailVerified && user.email.equals(DefaultCatalogDefaults.BOOTSTRAP_ADMIN_EMAIL, ignoreCase = true)) {
            return true
        }
        return try {
            val snap = db.collection("administrators").document(user.uid).get().await()
            snap.exists() && snap.getBoolean("active") == true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "administrators/${user.uid}")
            false
        }
    }

    // --- Public Customer Streams ---

    fun observeProducts(): Flow<List<ProductModel>> =
        db.collection("products")
            .orderBy("sortOrder", Query.Direction.ASCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(ProductModel::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                val fsError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                if (fsError == null && error is CancellationException) throw error
                handleFirestoreError(fsError ?: error, OperationType.LIST, "products")
                throw error
            }

    fun observeInventory(): Flow<List<InventoryModel>> =
        db.collection("inventory")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(InventoryModel::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                val fsError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                if (fsError == null && error is CancellationException) throw error
                handleFirestoreError(fsError ?: error, OperationType.LIST, "inventory")
                throw error
            }

    fun observeBusinessSettings(): Flow<BusinessSettingsModel> =
        db.collection("business_settings").document("default")
            .snapshots()
            .map { snapshot ->
                snapshot.toObject(BusinessSettingsModel::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: DefaultCatalogDefaults.defaultSettings
            }
            .catch { error ->
                val fsError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                if (fsError == null && error is CancellationException) throw error
                handleFirestoreError(fsError ?: error, OperationType.GET, "business_settings/default")
                throw error
            }

    fun observeOrderStatusTracker(orderId: String): Flow<OrderStatusTrackerModel?> {
        if (orderId.length < 20) return flow { emit(null) }
        return db.collection("order_status").document(orderId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(OrderStatusTrackerModel::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else null
            }
            .catch { error ->
                val fsError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                if (fsError == null && error is CancellationException) throw error
                handleFirestoreError(fsError ?: error, OperationType.GET, "order_status/$orderId")
                throw error
            }
    }

    suspend fun fetchOrderStatusOnce(orderId: String): OrderStatusTrackerModel? {
        if (orderId.length < 20) return null
        return try {
            val snap = db.collection("order_status").document(orderId).get().await()
            if (snap.exists()) {
                snap.toObject(OrderStatusTrackerModel::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "order_status/$orderId")
            null
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
        val path = "orders/$orderId"

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

            val orderPayload = mapOf(
                "orderNumber" to orderNumber,
                "customerName" to cleanName,
                "customerPhone" to cleanPhone,
                "desiredDate" to cleanDate,
                "additionalInstructions" to additionalInstructions.trim().take(400).ifBlank { null },
                "preparationMode" to preparationMode.label,
                "fulfillmentType" to fulfillmentType.rawValue,
                "neighborhood" to neighborhood.trim().take(100).ifBlank { null },
                "streetAndNumber" to streetAndNumber.trim().take(150).ifBlank { null },
                "deliveryReference" to deliveryReference.trim().take(200).ifBlank { null },
                "googleMapsLink" to googleMapsLink.trim().take(300).ifBlank { null },
                "pickupDate" to pickupDate.trim().take(40).ifBlank { null },
                "pickupTime" to pickupTime.trim().take(20).ifBlank { null },
                "items" to items.map { it.toMap() },
                "subtotalPyg" to subtotal,
                "totalAmountPyg" to subtotal,
                "status" to OrderStatus.PENDING_PAYMENT.rawValue,
                "whatsappUnlocked" to false,
                "paymentMethod" to "BANK_TRANSFER",
                "paymentBank" to settings.bankName,
                "paymentAlias" to settings.paymentAlias,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>

            val statusPayload = mapOf(
                "orderNumber" to orderNumber,
                "status" to OrderStatus.PENDING_PAYMENT.rawValue,
                "whatsappUnlocked" to false,
                "totalAmountPyg" to subtotal,
                "fulfillmentType" to fulfillmentType.rawValue,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            db.runBatch { batch ->
                batch.set(db.collection("orders").document(orderId), orderPayload)
                batch.set(db.collection("order_status").document(orderId), statusPayload)
            }.await()

            val savedOrder = LocalSavedOrder(
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
                createdAtMillis = System.currentTimeMillis()
            )
            Result.success(savedOrder)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // --- Admin Authenticated Streams ---

    fun observeAllOrders(): Flow<List<OrderModel>> =
        db.collection("orders")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(OrderModel::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                val fsError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                if (fsError == null && error is CancellationException) throw error
                handleFirestoreError(fsError ?: error, OperationType.LIST, "orders")
                throw error
            }

    fun observeAuditLogs(): Flow<List<PaymentAuditLogModel>> =
        db.collection("payment_audit_logs")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(60)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(PaymentAuditLogModel::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                val fsError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                if (fsError == null && error is CancellationException) throw error
                handleFirestoreError(fsError ?: error, OperationType.LIST, "payment_audit_logs")
                throw error
            }

    // --- Admin Seeding & Operations ---

    suspend fun seedInitialCatalogIfNeeded(): Result<Unit> {
        return try {
            val (uid, email) = requireAdminIdentity()
            val productsSnap = db.collection("products").limit(1).get().await()
            val settingsSnap = db.collection("business_settings").document("default").get().await()

            if (productsSnap.isEmpty || !settingsSnap.exists()) {
                db.runBatch { batch ->
                    if (productsSnap.isEmpty) {
                        for (prod in DefaultCatalogDefaults.defaultProducts) {
                            batch.set(
                                db.collection("products").document(prod.id),
                                mapOf(
                                    "name" to prod.name,
                                    "description" to prod.description,
                                    "pricePyg" to prod.pricePyg,
                                    "active" to prod.active,
                                    "imageKey" to prod.imageKey,
                                    "sortOrder" to prod.sortOrder,
                                    "createdAt" to FieldValue.serverTimestamp(),
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            )
                            batch.set(
                                db.collection("inventory").document(prod.id),
                                mapOf(
                                    "productName" to prod.name,
                                    "stock" to DefaultCatalogDefaults.INITIAL_STOCK,
                                    "lowStockThreshold" to 5L,
                                    "updatedBy" to uid,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            )
                        }
                    }
                    if (!settingsSnap.exists()) {
                        val s = DefaultCatalogDefaults.defaultSettings
                        batch.set(
                            db.collection("business_settings").document("default"),
                            mapOf(
                                "brandName" to s.brandName,
                                "instagramHandle" to s.instagramHandle,
                                "whatsappNumber" to s.whatsappNumber,
                                "bankName" to s.bankName,
                                "paymentAlias" to s.paymentAlias,
                                "deliveryCity" to s.deliveryCity,
                                "currency" to s.currency,
                                "acceptingOrders" to s.acceptingOrders,
                                "treasureCode" to s.treasureCode,
                                "treasureReward" to s.treasureReward,
                                "googleReviewUrl" to s.googleReviewUrl,
                                "pwaInstallUrl" to s.pwaInstallUrl,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                    // Ensure bootstrapped admin profile doc exists
                    batch.set(
                        db.collection("administrators").document(uid),
                        mapOf(
                            "uid" to uid,
                            "email" to email,
                            "displayName" to (auth.currentUser?.displayName ?: "Administrador"),
                            "role" to "SUPER_ADMIN",
                            "active" to true,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )
                }.await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "products/seed")
            Result.failure(e)
        }
    }

    /**
     * Manual Payment Confirmation:
     * Executes an atomic Firestore transaction that:
     * 1. Verifies the order is still PENDING_PAYMENT.
     * 2. Reads all inventory items and guarantees stock NEVER goes below 0.
     * 3. Deducts stock per flavor.
     * 4. Marks order and order_status as PAYMENT_CONFIRMED & unlocks WhatsApp.
     * 5. Records an immutable entry in payment_audit_logs.
     */
    suspend fun confirmOrderPayment(orderId: String, note: String = "Depósito Itaú verificado manualmente"): Result<Unit> {
        val path = "orders/$orderId"
        return try {
            val (uid, email) = requireAdminIdentity()
            require(orderId.isNotBlank()) { "ID de pedido inválido." }

            db.runTransaction { tx ->
                val orderRef = db.collection("orders").document(orderId)
                val statusRef = db.collection("order_status").document(orderId)
                val orderSnap = tx.get(orderRef)
                if (!orderSnap.exists()) {
                    throw IllegalStateException("El pedido no existe.")
                }
                val currentStatus = orderSnap.getString("status") ?: OrderStatus.PENDING_PAYMENT.rawValue
                if (currentStatus != OrderStatus.PENDING_PAYMENT.rawValue) {
                    throw IllegalStateException("El pedido ya se encuentra en estado: $currentStatus")
                }

                val orderNumber = orderSnap.getString("orderNumber") ?: orderId
                val totalAmountPyg = (orderSnap.get("totalAmountPyg") as? Number)?.toLong() ?: 0L

                @Suppress("UNCHECKED_CAST")
                val rawItems = orderSnap.get("items") as? List<Map<String, Any?>> ?: emptyList()
                val invUpdates = mutableListOf<Triple<com.google.firebase.firestore.DocumentReference, Long, String>>()

                for (rawItem in rawItems) {
                    val prodId = rawItem["productId"] as? String ?: continue
                    val prodName = rawItem["productName"] as? String ?: prodId
                    val qty = (rawItem["quantity"] as? Number)?.toLong() ?: 0L
                    if (qty <= 0L) continue

                    val invRef = db.collection("inventory").document(prodId)
                    val invSnap = tx.get(invRef)
                    val currentStock = if (invSnap.exists()) {
                        (invSnap.get("stock") as? Number)?.toLong() ?: 0L
                    } else {
                        DefaultCatalogDefaults.INITIAL_STOCK
                    }
                    val nextStock = currentStock - qty
                    if (nextStock < 0L) {
                        throw IllegalStateException(
                            "Stock insuficiente para $prodName (disponible: $currentStock, requerido: $qty)."
                        )
                    }
                    invUpdates.add(Triple(invRef, nextStock, prodName))
                }

                // Perform all writes after reads
                for ((invRef, nextStock, prodName) in invUpdates) {
                    tx.set(
                        invRef,
                        mapOf(
                            "productName" to prodName,
                            "stock" to nextStock,
                            "lowStockThreshold" to 5L,
                            "updatedBy" to uid,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                tx.update(
                    orderRef,
                    mapOf(
                        "status" to OrderStatus.PAYMENT_CONFIRMED.rawValue,
                        "whatsappUnlocked" to true,
                        "paymentConfirmedAt" to FieldValue.serverTimestamp(),
                        "confirmedByAdminId" to uid,
                        "confirmedByAdminEmail" to email,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                tx.update(
                    statusRef,
                    mapOf(
                        "status" to OrderStatus.PAYMENT_CONFIRMED.rawValue,
                        "whatsappUnlocked" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                val logRef = db.collection("payment_audit_logs").document("log_${UUID.randomUUID().toString().replace("-", "").take(20)}")
                tx.set(
                    logRef,
                    mapOf(
                        "orderId" to orderId,
                        "orderNumber" to orderNumber,
                        "adminId" to uid,
                        "adminEmail" to email,
                        "action" to "CONFIRM_PAYMENT",
                        "previousStatus" to OrderStatus.PENDING_PAYMENT.rawValue,
                        "newStatus" to OrderStatus.PAYMENT_CONFIRMED.rawValue,
                        "amountPyg" to totalAmountPyg,
                        "note" to note.take(300),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun advanceOrderStatus(order: OrderModel, nextStatus: OrderStatus): Result<Unit> {
        val path = "orders/${order.id}"
        return try {
            val (uid, email) = requireAdminIdentity()
            require(nextStatus != OrderStatus.PENDING_PAYMENT && nextStatus != OrderStatus.CANCELLED)

            db.runBatch { batch ->
                batch.update(
                    db.collection("orders").document(order.id),
                    mapOf(
                        "status" to nextStatus.rawValue,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.update(
                    db.collection("order_status").document(order.id),
                    mapOf(
                        "status" to nextStatus.rawValue,
                        "whatsappUnlocked" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                val logRef = db.collection("payment_audit_logs").document("log_${UUID.randomUUID().toString().replace("-", "").take(20)}")
                batch.set(
                    logRef,
                    mapOf(
                        "orderId" to order.id,
                        "orderNumber" to order.orderNumber,
                        "adminId" to uid,
                        "adminEmail" to email,
                        "action" to "STATUS_CHANGE",
                        "previousStatus" to order.status,
                        "newStatus" to nextStatus.rawValue,
                        "amountPyg" to order.totalAmountPyg,
                        "note" to "Estado cambiado a ${nextStatus.labelEs}",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun cancelOrder(order: OrderModel, reason: String): Result<Unit> {
        val path = "orders/${order.id}"
        return try {
            val (uid, email) = requireAdminIdentity()
            val cleanReason = reason.trim().ifBlank { "Cancelado por el administrador" }.take(200)

            db.runBatch { batch ->
                batch.update(
                    db.collection("orders").document(order.id),
                    mapOf(
                        "status" to OrderStatus.CANCELLED.rawValue,
                        "whatsappUnlocked" to false,
                        "cancellationReason" to cleanReason,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.update(
                    db.collection("order_status").document(order.id),
                    mapOf(
                        "status" to OrderStatus.CANCELLED.rawValue,
                        "whatsappUnlocked" to false,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                val logRef = db.collection("payment_audit_logs").document("log_${UUID.randomUUID().toString().replace("-", "").take(20)}")
                batch.set(
                    logRef,
                    mapOf(
                        "orderId" to order.id,
                        "orderNumber" to order.orderNumber,
                        "adminId" to uid,
                        "adminEmail" to email,
                        "action" to "CANCEL_ORDER",
                        "previousStatus" to order.status,
                        "newStatus" to OrderStatus.CANCELLED.rawValue,
                        "amountPyg" to order.totalAmountPyg,
                        "note" to cleanReason,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun adjustInventoryStock(productId: String, productName: String, newStock: Long, lowThreshold: Long): Result<Unit> {
        val path = "inventory/$productId"
        return try {
            val (uid, email) = requireAdminIdentity()
            require(newStock >= 0L) { "El stock no puede ser negativo." }
            require(lowThreshold >= 0L) { "La alerta mínima no puede ser negativa." }

            db.runBatch { batch ->
                batch.set(
                    db.collection("inventory").document(productId),
                    mapOf(
                        "productName" to productName,
                        "stock" to newStock,
                        "lowStockThreshold" to lowThreshold,
                        "updatedBy" to uid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                val logRef = db.collection("payment_audit_logs").document("log_${UUID.randomUUID().toString().replace("-", "").take(20)}")
                batch.set(
                    logRef,
                    mapOf(
                        "orderId" to productId,
                        "orderNumber" to "STOCK-$productId".take(32),
                        "adminId" to uid,
                        "adminEmail" to email,
                        "action" to "STOCK_ADJUSTMENT",
                        "previousStatus" to productName,
                        "newStatus" to "STOCK_$newStock",
                        "amountPyg" to 0L,
                        "note" to "Stock ajustado a $newStock unidades ($productName)",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
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
        val path = "products/$productId"
        return try {
            val (uid, _) = requireAdminIdentity()
            require(productId.matches(Regex("^[a-zA-Z0-9_\\-]+$"))) { "ID de producto inválido." }
            require(name.trim().length in 1..80) { "Nombre de pizza inválido." }
            require(pricePyg in 1..10_000_000) { "Precio debe ser mayor a 0 Gs." }

            if (isNew) {
                db.runBatch { batch ->
                    batch.set(
                        db.collection("products").document(productId),
                        mapOf(
                            "name" to name.trim(),
                            "description" to description.trim().take(300),
                            "pricePyg" to pricePyg,
                            "active" to active,
                            "imageKey" to imageKey,
                            "sortOrder" to sortOrder,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    batch.set(
                        db.collection("inventory").document(productId),
                        mapOf(
                            "productName" to name.trim(),
                            "stock" to DefaultCatalogDefaults.INITIAL_STOCK,
                            "lowStockThreshold" to 5L,
                            "updatedBy" to uid,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }.await()
            } else {
                db.collection("products").document(productId).update(
                    mapOf(
                        "name" to name.trim(),
                        "description" to description.trim().take(300),
                        "pricePyg" to pricePyg,
                        "active" to active,
                        "imageKey" to imageKey,
                        "sortOrder" to sortOrder,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateBusinessSettings(settings: BusinessSettingsModel): Result<Unit> {
        val path = "business_settings/default"
        return try {
            requireAdminIdentity()
            db.collection("business_settings").document("default").set(
                mapOf(
                    "brandName" to settings.brandName.trim().take(80),
                    "instagramHandle" to settings.instagramHandle.trim().take(60),
                    "whatsappNumber" to settings.whatsappNumber.trim().take(20),
                    "bankName" to settings.bankName.trim().take(80),
                    "paymentAlias" to settings.paymentAlias.trim().take(120),
                    "deliveryCity" to settings.deliveryCity.trim().take(80),
                    "currency" to "PYG",
                    "acceptingOrders" to settings.acceptingOrders,
                    "treasureCode" to settings.treasureCode.trim().uppercase().take(40).ifBlank { "PIZZACLUB-ORO" },
                    "treasureReward" to settings.treasureReward.trim().take(160).ifBlank { "Dip Artesanal + 10% OFF en tu próxima pizza" },
                    "googleReviewUrl" to settings.googleReviewUrl.trim().take(300)
                        .ifBlank { DefaultCatalogDefaults.defaultSettings.googleReviewUrl },
                    "pwaInstallUrl" to settings.pwaInstallUrl.trim().take(300)
                        .ifBlank { DefaultCatalogDefaults.defaultSettings.pwaInstallUrl },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }
}
