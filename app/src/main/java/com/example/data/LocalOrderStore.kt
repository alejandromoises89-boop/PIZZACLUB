package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.pizzaClubDataStore by preferencesDataStore(name = "pizza_club_customer_orders")

class LocalOrderStore(private val context: Context) {
    private val ordersKey = stringPreferencesKey("saved_orders_json")
    private val pointsBalanceKey = intPreferencesKey("pizza_club_points_balance")

    val savedOrdersFlow: Flow<List<LocalSavedOrder>> = context.pizzaClubDataStore.data.map { prefs ->
        val raw = prefs[ordersKey] ?: return@map emptyList()
        parseOrdersJson(raw)
    }

    val pointsBalanceFlow: Flow<Int> = context.pizzaClubDataStore.data.map { prefs ->
        prefs[pointsBalanceKey] ?: parseOrdersJson(prefs[ordersKey] ?: "[]").computePizzaClubPoints()
    }

    suspend fun saveOrder(order: LocalSavedOrder) {
        context.pizzaClubDataStore.edit { prefs ->
            val current = parseOrdersJson(prefs[ordersKey] ?: "[]").toMutableList()
            current.removeAll { it.orderId == order.orderId }
            current.add(0, order)
            val trimmed = current.take(25)
            prefs[ordersKey] = serializeOrdersJson(trimmed)
            prefs[pointsBalanceKey] = trimmed.computePizzaClubPoints()
        }
    }

    suspend fun updateOrderStatus(orderId: String, status: String, whatsappUnlocked: Boolean) {
        context.pizzaClubDataStore.edit { prefs ->
            val current = parseOrdersJson(prefs[ordersKey] ?: "[]")
            val now = System.currentTimeMillis()
            val updated = current.map { existing ->
                if (existing.orderId == orderId) {
                    val stageMillis = if (existing.status != status) now else existing.stageUpdatedAtMillis
                    existing.copy(
                        status = status,
                        whatsappUnlocked = whatsappUnlocked,
                        stageUpdatedAtMillis = stageMillis
                    )
                } else {
                    existing
                }
            }
            prefs[ordersKey] = serializeOrdersJson(updated)
            prefs[pointsBalanceKey] = updated.computePizzaClubPoints()
        }
    }

    private fun List<LocalSavedOrder>.computePizzaClubPoints(): Int =
        filter { it.status != OrderStatus.CANCELLED.rawValue }
            .sumOf { order -> order.items.sumOf { it.quantity.toInt().coerceAtLeast(0) } }

    private fun serializeOrdersJson(orders: List<LocalSavedOrder>): String {
        val array = JSONArray()
        for (o in orders) {
            val itemsArray = JSONArray()
            for (item in o.items) {
                itemsArray.put(
                    JSONObject().apply {
                        put("productId", item.productId)
                        put("productName", item.productName)
                        put("unitPricePyg", item.unitPricePyg)
                        put("quantity", item.quantity)
                        put("lineTotalPyg", item.lineTotalPyg)
                    }
                )
            }
            array.put(
                JSONObject().apply {
                    put("orderId", o.orderId)
                    put("orderNumber", o.orderNumber)
                    put("customerName", o.customerName)
                    put("customerPhone", o.customerPhone)
                    put("desiredDate", o.desiredDate)
                    put("additionalInstructions", o.additionalInstructions)
                    put("preparationMode", o.preparationMode)
                    put("fulfillmentType", o.fulfillmentType)
                    put("neighborhood", o.neighborhood)
                    put("streetAndNumber", o.streetAndNumber)
                    put("deliveryReference", o.deliveryReference)
                    put("googleMapsLink", o.googleMapsLink)
                    put("pickupDate", o.pickupDate)
                    put("pickupTime", o.pickupTime)
                    put("items", itemsArray)
                    put("totalAmountPyg", o.totalAmountPyg)
                    put("paymentBank", o.paymentBank)
                    put("paymentAlias", o.paymentAlias)
                    put("whatsappNumber", o.whatsappNumber)
                    put("status", o.status)
                    put("whatsappUnlocked", o.whatsappUnlocked)
                    put("createdAtMillis", o.createdAtMillis)
                    put("stageUpdatedAtMillis", o.stageUpdatedAtMillis)
                }
            )
        }
        return array.toString()
    }

    private fun parseOrdersJson(raw: String): List<LocalSavedOrder> {
        return try {
            val array = JSONArray(raw)
            val result = mutableListOf<LocalSavedOrder>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val itemsJson = obj.optJSONArray("items") ?: JSONArray()
                val items = mutableListOf<OrderLineItem>()
                for (j in 0 until itemsJson.length()) {
                    val itemObj = itemsJson.optJSONObject(j) ?: continue
                    items.add(
                        OrderLineItem(
                            productId = itemObj.optString("productId"),
                            productName = itemObj.optString("productName"),
                            unitPricePyg = itemObj.optLong("unitPricePyg"),
                            quantity = itemObj.optLong("quantity"),
                            lineTotalPyg = itemObj.optLong("lineTotalPyg")
                        )
                    )
                }
                result.add(
                    LocalSavedOrder(
                        orderId = obj.optString("orderId"),
                        orderNumber = obj.optString("orderNumber"),
                        customerName = obj.optString("customerName"),
                        customerPhone = obj.optString("customerPhone"),
                        desiredDate = obj.optString("desiredDate"),
                        additionalInstructions = obj.optString("additionalInstructions"),
                        preparationMode = obj.optString("preparationMode"),
                        fulfillmentType = obj.optString("fulfillmentType"),
                        neighborhood = obj.optString("neighborhood"),
                        streetAndNumber = obj.optString("streetAndNumber"),
                        deliveryReference = obj.optString("deliveryReference"),
                        googleMapsLink = obj.optString("googleMapsLink"),
                        pickupDate = obj.optString("pickupDate"),
                        pickupTime = obj.optString("pickupTime"),
                        items = items,
                        totalAmountPyg = obj.optLong("totalAmountPyg"),
                        paymentBank = obj.optString("paymentBank", "Itaú Paraguay"),
                        paymentAlias = obj.optString("paymentAlias", DefaultCatalogDefaults.BOOTSTRAP_ADMIN_EMAIL),
                        whatsappNumber = obj.optString("whatsappNumber", "59593551936"),
                        status = obj.optString("status", OrderStatus.PENDING_PAYMENT.rawValue),
                        whatsappUnlocked = obj.optBoolean("whatsappUnlocked", false),
                        createdAtMillis = obj.optLong("createdAtMillis", System.currentTimeMillis()),
                        stageUpdatedAtMillis = obj.optLong(
                            "stageUpdatedAtMillis",
                            obj.optLong("createdAtMillis", System.currentTimeMillis())
                        )
                    )
                )
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }
}
