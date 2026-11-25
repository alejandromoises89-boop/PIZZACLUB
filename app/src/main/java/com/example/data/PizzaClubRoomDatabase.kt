package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val pricePyg: Long,
    val active: Boolean,
    val imageKey: String,
    val sortOrder: Long,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

@Entity(tableName = "inventory")
data class InventoryEntity(
    @PrimaryKey val id: String,
    val productName: String,
    val stock: Long,
    val lowStockThreshold: Long,
    val updatedBy: String,
    val updatedAtMillis: Long
)

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey val id: String,
    val orderNumber: String,
    val customerName: String,
    val customerPhone: String,
    val desiredDate: String,
    val additionalInstructions: String?,
    val preparationMode: String,
    val fulfillmentType: String,
    val neighborhood: String?,
    val streetAndNumber: String?,
    val deliveryReference: String?,
    val googleMapsLink: String?,
    val pickupDate: String?,
    val pickupTime: String?,
    val itemsJson: String,
    val subtotalPyg: Long,
    val totalAmountPyg: Long,
    val status: String,
    val whatsappUnlocked: Boolean,
    val paymentMethod: String,
    val paymentBank: String,
    val paymentAlias: String,
    val paymentConfirmedAtMillis: Long?,
    val confirmedByAdminId: String?,
    val confirmedByAdminEmail: String?,
    val cancellationReason: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

@Entity(tableName = "payment_audit_logs")
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val orderId: String,
    val orderNumber: String,
    val adminId: String,
    val adminEmail: String,
    val action: String,
    val previousStatus: String,
    val newStatus: String,
    val amountPyg: Long,
    val note: String?,
    val createdAtMillis: Long
)

@Entity(tableName = "business_settings")
data class BusinessSettingsEntity(
    @PrimaryKey val id: String,
    val brandName: String,
    val instagramHandle: String,
    val whatsappNumber: String,
    val bankName: String,
    val paymentAlias: String,
    val deliveryCity: String,
    val currency: String,
    val acceptingOrders: Boolean,
    val treasureCode: String,
    val treasureReward: String,
    val googleReviewUrl: String,
    val pwaInstallUrl: String,
    val updatedAtMillis: Long
)

@Dao
interface PizzaClubDao {
    @Query("SELECT * FROM products ORDER BY sortOrder ASC")
    fun observeProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products")
    suspend fun getProductsCount(): List<ProductEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProducts(items: List<ProductEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProduct(item: ProductEntity)

    @Query("SELECT * FROM inventory")
    fun observeInventory(): Flow<List<InventoryEntity>>

    @Query("SELECT * FROM inventory WHERE id = :id LIMIT 1")
    suspend fun getInventoryById(id: String): InventoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInventory(items: List<InventoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInventoryItem(item: InventoryEntity)

    @Query("SELECT * FROM business_settings WHERE id = 'default' LIMIT 1")
    fun observeSettings(): Flow<BusinessSettingsEntity?>

    @Query("SELECT * FROM business_settings WHERE id = 'default' LIMIT 1")
    suspend fun getSettingsOnce(): BusinessSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: BusinessSettingsEntity)

    @Query("SELECT * FROM orders ORDER BY createdAtMillis DESC")
    fun observeOrders(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE id = :orderId LIMIT 1")
    fun observeOrderById(orderId: String): Flow<OrderEntity?>

    @Query("SELECT * FROM orders WHERE id = :orderId LIMIT 1")
    suspend fun getOrderById(orderId: String): OrderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOrders(orders: List<OrderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOrder(order: OrderEntity)

    @Query("SELECT * FROM payment_audit_logs ORDER BY createdAtMillis DESC LIMIT 60")
    fun observeAuditLogs(): Flow<List<AuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAuditLogs(logs: List<AuditLogEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLogEntity)

    @Transaction
    suspend fun confirmOrderPaymentLocally(
        orderId: String,
        adminId: String,
        adminEmail: String,
        note: String,
        nowMillis: Long
    ) {
        val order = getOrderById(orderId) ?: throw IllegalStateException("El pedido no existe.")
        if (order.status != OrderStatus.PENDING_PAYMENT.rawValue) {
            throw IllegalStateException("El pedido ya se encuentra en estado: ${order.status}")
        }
        val items = decodeOrderItemsJson(order.itemsJson)
        val pendingUpdates = mutableListOf<InventoryEntity>()
        for (line in items) {
            val inv = getInventoryById(line.productId) ?: InventoryEntity(
                id = line.productId,
                productName = line.productName,
                stock = DefaultCatalogDefaults.INITIAL_STOCK,
                lowStockThreshold = 5L,
                updatedBy = adminId,
                updatedAtMillis = nowMillis
            )
            val nextStock = inv.stock - line.quantity
            if (nextStock < 0L) {
                throw IllegalStateException("Stock insuficiente para ${line.productName} (disponible: ${inv.stock}, requerido: ${line.quantity}).")
            }
            pendingUpdates.add(inv.copy(stock = nextStock, updatedBy = adminId, updatedAtMillis = nowMillis))
        }
        for (invUpdate in pendingUpdates) {
            upsertInventoryItem(invUpdate)
        }
        upsertOrder(
            order.copy(
                status = OrderStatus.PAYMENT_CONFIRMED.rawValue,
                whatsappUnlocked = true,
                paymentConfirmedAtMillis = nowMillis,
                confirmedByAdminId = adminId,
                confirmedByAdminEmail = adminEmail,
                updatedAtMillis = nowMillis
            )
        )
        insertAuditLog(
            AuditLogEntity(
                id = "log_${UUID.randomUUID().toString().replace("-", "").take(20)}",
                orderId = orderId,
                orderNumber = order.orderNumber,
                adminId = adminId,
                adminEmail = adminEmail,
                action = "CONFIRM_PAYMENT",
                previousStatus = OrderStatus.PENDING_PAYMENT.rawValue,
                newStatus = OrderStatus.PAYMENT_CONFIRMED.rawValue,
                amountPyg = order.totalAmountPyg,
                note = note.take(300),
                createdAtMillis = nowMillis
            )
        )
    }
}

@Database(
    entities = [
        ProductEntity::class,
        InventoryEntity::class,
        OrderEntity::class,
        AuditLogEntity::class,
        BusinessSettingsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PizzaClubDatabase : RoomDatabase() {
    abstract fun pizzaClubDao(): PizzaClubDao

    companion object {
        @Volatile
        private var INSTANCE: PizzaClubDatabase? = null

        fun getInstance(context: Context): PizzaClubDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PizzaClubDatabase::class.java,
                    "pizza_club_local.db"
                ).fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

fun encodeOrderItemsJson(items: List<OrderLineItem>): String {
    val arr = JSONArray()
    for (item in items) {
        arr.put(
            JSONObject().apply {
                put("productId", item.productId)
                put("productName", item.productName)
                put("unitPricePyg", item.unitPricePyg)
                put("quantity", item.quantity)
                put("lineTotalPyg", item.lineTotalPyg)
            }
        )
    }
    return arr.toString()
}

fun decodeOrderItemsJson(raw: String): List<OrderLineItem> {
    return try {
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                add(
                    OrderLineItem(
                        productId = obj.optString("productId"),
                        productName = obj.optString("productName"),
                        unitPricePyg = obj.optLong("unitPricePyg"),
                        quantity = obj.optLong("quantity"),
                        lineTotalPyg = obj.optLong("lineTotalPyg")
                    )
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

fun ProductEntity.toModel() = ProductModel(
    id = id,
    name = name,
    description = description,
    pricePyg = pricePyg,
    active = active,
    imageKey = imageKey,
    sortOrder = sortOrder,
    createdAt = EpochTimestamp(createdAtMillis),
    updatedAt = EpochTimestamp(updatedAtMillis)
)

fun InventoryEntity.toModel() = InventoryModel(
    id = id,
    productName = productName,
    stock = stock,
    lowStockThreshold = lowStockThreshold,
    updatedBy = updatedBy,
    updatedAt = EpochTimestamp(updatedAtMillis)
)

fun OrderEntity.toModel() = OrderModel(
    id = id,
    orderNumber = orderNumber,
    customerName = customerName,
    customerPhone = customerPhone,
    desiredDate = desiredDate,
    additionalInstructions = additionalInstructions,
    preparationMode = preparationMode,
    fulfillmentType = fulfillmentType,
    neighborhood = neighborhood,
    streetAndNumber = streetAndNumber,
    deliveryReference = deliveryReference,
    googleMapsLink = googleMapsLink,
    pickupDate = pickupDate,
    pickupTime = pickupTime,
    items = decodeOrderItemsJson(itemsJson),
    subtotalPyg = subtotalPyg,
    totalAmountPyg = totalAmountPyg,
    status = status,
    whatsappUnlocked = whatsappUnlocked,
    paymentMethod = paymentMethod,
    paymentBank = paymentBank,
    paymentAlias = paymentAlias,
    paymentConfirmedAt = paymentConfirmedAtMillis?.let { EpochTimestamp(it) },
    confirmedByAdminId = confirmedByAdminId,
    confirmedByAdminEmail = confirmedByAdminEmail,
    cancellationReason = cancellationReason,
    createdAt = EpochTimestamp(createdAtMillis),
    updatedAt = EpochTimestamp(updatedAtMillis)
)

fun OrderEntity.toTrackerModel() = OrderStatusTrackerModel(
    id = id,
    orderNumber = orderNumber,
    status = status,
    whatsappUnlocked = whatsappUnlocked,
    totalAmountPyg = totalAmountPyg,
    fulfillmentType = fulfillmentType,
    createdAt = EpochTimestamp(createdAtMillis),
    updatedAt = EpochTimestamp(updatedAtMillis)
)

fun AuditLogEntity.toModel() = PaymentAuditLogModel(
    id = id,
    orderId = orderId,
    orderNumber = orderNumber,
    adminId = adminId,
    adminEmail = adminEmail,
    action = action,
    previousStatus = previousStatus,
    newStatus = newStatus,
    amountPyg = amountPyg,
    note = note,
    createdAt = EpochTimestamp(createdAtMillis)
)

fun BusinessSettingsEntity.toModel() = BusinessSettingsModel(
    id = id,
    brandName = brandName,
    instagramHandle = instagramHandle,
    whatsappNumber = whatsappNumber,
    bankName = bankName,
    paymentAlias = paymentAlias,
    deliveryCity = deliveryCity,
    currency = currency,
    acceptingOrders = acceptingOrders,
    treasureCode = treasureCode,
    treasureReward = treasureReward,
    googleReviewUrl = googleReviewUrl,
    pwaInstallUrl = pwaInstallUrl,
    updatedAt = EpochTimestamp(updatedAtMillis)
)
