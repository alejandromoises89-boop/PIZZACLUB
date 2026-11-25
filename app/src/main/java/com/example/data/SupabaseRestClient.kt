package com.example.data

import android.content.Context
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class CloudDbConfig(
    val url: String,
    val anonKey: String
) {
    val isConfigured: Boolean
        get() = url.startsWith("https://") &&
            !url.contains("YOUR_", ignoreCase = true) &&
            anonKey.length > 20 &&
            !anonKey.contains("YOUR_", ignoreCase = true)
}

class SupabaseRestClient(private val context: Context) {
    private val prefs by lazy {
        context.applicationContext.getSharedPreferences("pizza_club_cloud_prefs", Context.MODE_PRIVATE)
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun currentConfig(): CloudDbConfig {
        val savedUrl = prefs.getString("supabase_url", "")?.trim().orEmpty()
        val savedKey = prefs.getString("supabase_anon_key", "")?.trim().orEmpty()
        if (savedUrl.startsWith("https://") && savedKey.length > 20) {
            return CloudDbConfig(savedUrl.trimEnd('/'), savedKey)
        }
        val envUrl = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
        val envKey = BuildConfig.SUPABASE_ANON_KEY.trim()
        return CloudDbConfig(envUrl, envKey)
    }

    fun saveRuntimeConfig(url: String, anonKey: String) {
        prefs.edit()
            .putString("supabase_url", url.trim().trimEnd('/'))
            .putString("supabase_anon_key", anonKey.trim())
            .apply()
    }

    fun clearRuntimeConfig() {
        prefs.edit().remove("supabase_url").remove("supabase_anon_key").apply()
    }

    fun isConfigured(): Boolean = currentConfig().isConfigured

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}: ${bodyStr.take(240)}")
            }
            bodyStr
        }
    }

    private fun baseRequest(cfg: CloudDbConfig, pathWithQuery: String): Request.Builder {
        return Request.Builder()
            .url("${cfg.url}/rest/v1/$pathWithQuery")
            .header("apikey", cfg.anonKey)
            .header("Authorization", "Bearer ${cfg.anonKey}")
            .header("Content-Type", "application/json")
    }

    suspend fun fetchProducts(): List<ProductEntity> {
        val cfg = currentConfig()
        if (!cfg.isConfigured) return emptyList()
        val body = execute(baseRequest(cfg, "products?select=*&order=sort_order.asc").get().build())
        val arr = JSONArray(body)
        val now = System.currentTimeMillis()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    ProductEntity(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        description = o.optString("description"),
                        pricePyg = o.optLong("price_pyg", 50_000L),
                        active = o.optBoolean("active", true),
                        imageKey = o.optString("image_key", "pepperoni"),
                        sortOrder = o.optLong("sort_order", i.toLong()),
                        createdAtMillis = o.optLong("created_at_ms", now),
                        updatedAtMillis = o.optLong("updated_at_ms", now)
                    )
                )
            }
        }
    }

    suspend fun fetchInventory(): List<InventoryEntity> {
        val cfg = currentConfig()
        if (!cfg.isConfigured) return emptyList()
        val body = execute(baseRequest(cfg, "inventory?select=*").get().build())
        val arr = JSONArray(body)
        val now = System.currentTimeMillis()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    InventoryEntity(
                        id = o.optString("id"),
                        productName = o.optString("product_name"),
                        stock = o.optLong("stock", 20L),
                        lowStockThreshold = o.optLong("low_stock_threshold", 5L),
                        updatedBy = o.optString("updated_by", "system"),
                        updatedAtMillis = o.optLong("updated_at_ms", now)
                    )
                )
            }
        }
    }

    suspend fun fetchBusinessSettings(): BusinessSettingsEntity? {
        val cfg = currentConfig()
        if (!cfg.isConfigured) return null
        val body = execute(baseRequest(cfg, "business_settings?id=eq.default&select=*&limit=1").get().build())
        val arr = JSONArray(body)
        if (arr.length() == 0) return null
        val o = arr.getJSONObject(0)
        return BusinessSettingsEntity(
            id = o.optString("id", "default"),
            brandName = o.optString("brand_name", "Pizza Club"),
            instagramHandle = o.optString("instagram_handle", "@Pizza.Club1"),
            whatsappNumber = o.optString("whatsapp_number", "59593551936"),
            bankName = o.optString("bank_name", "Itaú Paraguay"),
            paymentAlias = o.optString("payment_alias", DefaultCatalogDefaults.BOOTSTRAP_ADMIN_EMAIL),
            deliveryCity = o.optString("delivery_city", "Ciudad del Este"),
            currency = o.optString("currency", "PYG"),
            acceptingOrders = o.optBoolean("accepting_orders", true),
            treasureCode = o.optString("treasure_code", "PIZZACLUB-ORO"),
            treasureReward = o.optString("treasure_reward", DefaultCatalogDefaults.defaultSettings.treasureReward),
            googleReviewUrl = o.optString("google_review_url", DefaultCatalogDefaults.defaultSettings.googleReviewUrl),
            pwaInstallUrl = o.optString("pwa_install_url", DefaultCatalogDefaults.defaultSettings.pwaInstallUrl),
            updatedAtMillis = o.optLong("updated_at_ms", System.currentTimeMillis())
        )
    }

    suspend fun fetchOrderStatusOnce(orderId: String): OrderStatusTrackerModel? {
        val cfg = currentConfig()
        if (!cfg.isConfigured) return null
        val enc = URLEncoder.encode(orderId, "UTF-8")
        val body = execute(baseRequest(cfg, "order_status?id=eq.$enc&select=*&limit=1").get().build())
        val arr = JSONArray(body)
        if (arr.length() == 0) return null
        val o = arr.getJSONObject(0)
        return OrderStatusTrackerModel(
            id = o.optString("id"),
            orderNumber = o.optString("order_number"),
            status = o.optString("status", OrderStatus.PENDING_PAYMENT.rawValue),
            whatsappUnlocked = o.optBoolean("whatsapp_unlocked", false),
            totalAmountPyg = o.optLong("total_amount_pyg", 0L),
            fulfillmentType = o.optString("fulfillment_type", FulfillmentType.DELIVERY.rawValue),
            createdAt = EpochTimestamp(o.optLong("created_at_ms", System.currentTimeMillis())),
            updatedAt = EpochTimestamp(o.optLong("updated_at_ms", System.currentTimeMillis()))
        )
    }

    suspend fun fetchAllOrders(): List<OrderEntity> {
        val cfg = currentConfig()
        if (!cfg.isConfigured) return emptyList()
        val body = execute(baseRequest(cfg, "orders?select=*&order=created_at_ms.desc&limit=100").get().build())
        val arr = JSONArray(body)
        val now = System.currentTimeMillis()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val itemsRaw = o.opt("items")
                val itemsJsonStr = when (itemsRaw) {
                    is JSONArray -> itemsRaw.toString()
                    is String -> itemsRaw
                    else -> "[]"
                }
                add(
                    OrderEntity(
                        id = o.optString("id"),
                        orderNumber = o.optString("order_number"),
                        customerName = o.optString("customer_name"),
                        customerPhone = o.optString("customer_phone"),
                        desiredDate = o.optString("desired_date"),
                        additionalInstructions = o.optString("additional_instructions").ifBlank { null },
                        preparationMode = o.optString("preparation_mode", PreparationMode.READY_TO_EAT.label),
                        fulfillmentType = o.optString("fulfillment_type", FulfillmentType.DELIVERY.rawValue),
                        neighborhood = o.optString("neighborhood").ifBlank { null },
                        streetAndNumber = o.optString("street_and_number").ifBlank { null },
                        deliveryReference = o.optString("delivery_reference").ifBlank { null },
                        googleMapsLink = o.optString("google_maps_link").ifBlank { null },
                        pickupDate = o.optString("pickup_date").ifBlank { null },
                        pickupTime = o.optString("pickup_time").ifBlank { null },
                        itemsJson = itemsJsonStr,
                        subtotalPyg = o.optLong("subtotal_pyg", 0L),
                        totalAmountPyg = o.optLong("total_amount_pyg", 0L),
                        status = o.optString("status", OrderStatus.PENDING_PAYMENT.rawValue),
                        whatsappUnlocked = o.optBoolean("whatsapp_unlocked", false),
                        paymentMethod = o.optString("payment_method", "BANK_TRANSFER"),
                        paymentBank = o.optString("payment_bank", "Itaú Paraguay"),
                        paymentAlias = o.optString("payment_alias", DefaultCatalogDefaults.BOOTSTRAP_ADMIN_EMAIL),
                        paymentConfirmedAtMillis = if (o.isNull("payment_confirmed_at_ms")) null else o.optLong("payment_confirmed_at_ms"),
                        confirmedByAdminId = o.optString("confirmed_by_admin_id").ifBlank { null },
                        confirmedByAdminEmail = o.optString("confirmed_by_admin_email").ifBlank { null },
                        cancellationReason = o.optString("cancellation_reason").ifBlank { null },
                        createdAtMillis = o.optLong("created_at_ms", now),
                        updatedAtMillis = o.optLong("updated_at_ms", now)
                    )
                )
            }
        }
    }

    suspend fun fetchAuditLogs(): List<AuditLogEntity> {
        val cfg = currentConfig()
        if (!cfg.isConfigured) return emptyList()
        val body = execute(baseRequest(cfg, "payment_audit_logs?select=*&order=created_at_ms.desc&limit=60").get().build())
        val arr = JSONArray(body)
        val now = System.currentTimeMillis()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    AuditLogEntity(
                        id = o.optString("id"),
                        orderId = o.optString("order_id"),
                        orderNumber = o.optString("order_number"),
                        adminId = o.optString("admin_id"),
                        adminEmail = o.optString("admin_email"),
                        action = o.optString("action"),
                        previousStatus = o.optString("previous_status"),
                        newStatus = o.optString("new_status"),
                        amountPyg = o.optLong("amount_pyg", 0L),
                        note = o.optString("note").ifBlank { null },
                        createdAtMillis = o.optLong("created_at_ms", now)
                    )
                )
            }
        }
    }

    suspend fun insertPreOrder(order: OrderEntity) {
        val cfg = currentConfig()
        if (!cfg.isConfigured) return
        val orderObj = JSONObject().apply {
            put("id", order.id)
            put("order_number", order.orderNumber)
            put("customer_name", order.customerName)
            put("customer_phone", order.customerPhone)
            put("desired_date", order.desiredDate)
            put("additional_instructions", order.additionalInstructions)
            put("preparation_mode", order.preparationMode)
            put("fulfillment_type", order.fulfillmentType)
            put("neighborhood", order.neighborhood)
            put("street_and_number", order.streetAndNumber)
            put("delivery_reference", order.deliveryReference)
            put("google_maps_link", order.googleMapsLink)
            put("pickup_date", order.pickupDate)
            put("pickup_time", order.pickupTime)
            put("items", JSONArray(order.itemsJson))
            put("subtotal_pyg", order.subtotalPyg)
            put("total_amount_pyg", order.totalAmountPyg)
            put("status", order.status)
            put("whatsapp_unlocked", order.whatsappUnlocked)
            put("payment_method", order.paymentMethod)
            put("payment_bank", order.paymentBank)
            put("payment_alias", order.paymentAlias)
            put("created_at_ms", order.createdAtMillis)
            put("updated_at_ms", order.updatedAtMillis)
        }
        execute(
            baseRequest(cfg, "orders")
                .header("Prefer", "return=minimal")
                .post(orderObj.toString().toRequestBody(jsonMediaType))
                .build()
        )
        val statusObj = JSONObject().apply {
            put("id", order.id)
            put("order_number", order.orderNumber)
            put("status", order.status)
            put("whatsapp_unlocked", order.whatsappUnlocked)
            put("total_amount_pyg", order.totalAmountPyg)
            put("fulfillment_type", order.fulfillmentType)
            put("created_at_ms", order.createdAtMillis)
            put("updated_at_ms", order.updatedAtMillis)
        }
        execute(
            baseRequest(cfg, "order_status")
                .header("Prefer", "return=minimal")
                .post(statusObj.toString().toRequestBody(jsonMediaType))
                .build()
        )
    }

    suspend fun patchOrderAndStatus(
        orderId: String,
        orderPatch: JSONObject,
        statusPatch: JSONObject
    ) {
        val cfg = currentConfig()
        if (!cfg.isConfigured) return
        val enc = URLEncoder.encode(orderId, "UTF-8")
        execute(
            baseRequest(cfg, "orders?id=eq.$enc")
                .header("Prefer", "return=minimal")
                .patch(orderPatch.toString().toRequestBody(jsonMediaType))
                .build()
        )
        execute(
            baseRequest(cfg, "order_status?id=eq.$enc")
                .header("Prefer", "return=minimal")
                .patch(statusPatch.toString().toRequestBody(jsonMediaType))
                .build()
        )
    }

    suspend fun upsertInventoryItem(item: InventoryEntity) {
        val cfg = currentConfig()
        if (!cfg.isConfigured) return
        val obj = JSONObject().apply {
            put("id", item.id)
            put("product_name", item.productName)
            put("stock", item.stock)
            put("low_stock_threshold", item.lowStockThreshold)
            put("updated_by", item.updatedBy)
            put("updated_at_ms", item.updatedAtMillis)
        }
        execute(
            baseRequest(cfg, "inventory?on_conflict=id")
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(obj.toString().toRequestBody(jsonMediaType))
                .build()
        )
    }

    suspend fun upsertProduct(product: ProductEntity) {
        val cfg = currentConfig()
        if (!cfg.isConfigured) return
        val obj = JSONObject().apply {
            put("id", product.id)
            put("name", product.name)
            put("description", product.description)
            put("price_pyg", product.pricePyg)
            put("active", product.active)
            put("image_key", product.imageKey)
            put("sort_order", product.sortOrder)
            put("created_at_ms", product.createdAtMillis)
            put("updated_at_ms", product.updatedAtMillis)
        }
        execute(
            baseRequest(cfg, "products?on_conflict=id")
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(obj.toString().toRequestBody(jsonMediaType))
                .build()
        )
    }

    suspend fun upsertBusinessSettings(settings: BusinessSettingsEntity) {
        val cfg = currentConfig()
        if (!cfg.isConfigured) return
        val obj = JSONObject().apply {
            put("id", settings.id)
            put("brand_name", settings.brandName)
            put("instagram_handle", settings.instagramHandle)
            put("whatsapp_number", settings.whatsappNumber)
            put("bank_name", settings.bankName)
            put("payment_alias", settings.paymentAlias)
            put("delivery_city", settings.deliveryCity)
            put("currency", settings.currency)
            put("accepting_orders", settings.acceptingOrders)
            put("treasure_code", settings.treasureCode)
            put("treasure_reward", settings.treasureReward)
            put("google_review_url", settings.googleReviewUrl)
            put("pwa_install_url", settings.pwaInstallUrl)
            put("updated_at_ms", settings.updatedAtMillis)
        }
        execute(
            baseRequest(cfg, "business_settings?on_conflict=id")
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(obj.toString().toRequestBody(jsonMediaType))
                .build()
        )
    }

    suspend fun insertAuditLog(log: AuditLogEntity) {
        val cfg = currentConfig()
        if (!cfg.isConfigured) return
        val obj = JSONObject().apply {
            put("id", log.id)
            put("order_id", log.orderId)
            put("order_number", log.orderNumber)
            put("admin_id", log.adminId)
            put("admin_email", log.adminEmail)
            put("action", log.action)
            put("previous_status", log.previousStatus)
            put("new_status", log.newStatus)
            put("amount_pyg", log.amountPyg)
            put("note", log.note)
            put("created_at_ms", log.createdAtMillis)
        }
        execute(
            baseRequest(cfg, "payment_audit_logs")
                .header("Prefer", "return=minimal")
                .post(obj.toString().toRequestBody(jsonMediaType))
                .build()
        )
    }
}
