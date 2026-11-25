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
import java.security.SecureRandom

data class StatusTransition(
    val orderId: String,
    val orderNumber: String,
    val previousStatus: String,
    val newStatus: String
)

data class RedeemedLoyaltyCoupon(
    val code: String,
    val redeemedAtMillis: Long,
    val pointsCost: Int = LoyaltyProgramCalculator.LOYALTY_GOAL
)

data class LoyaltyProgramState(
    val balance: Int = 0,
    val totalEarned: Int = 0,
    val redeemedPoints: Int = 0,
    val activeCouponCode: String? = null,
    val redeemedCoupons: List<RedeemedLoyaltyCoupon> = emptyList()
)

object LoyaltyProgramCalculator {
    const val LOYALTY_GOAL = 10
    private const val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val random = SecureRandom()

    fun generateUniqueCouponCode(): String {
        val part1 = buildString(4) {
            repeat(4) { append(CODE_CHARS[random.nextInt(CODE_CHARS.length)]) }
        }
        val part2 = buildString(4) {
            repeat(4) { append(CODE_CHARS[random.nextInt(CODE_CHARS.length)]) }
        }
        return "PC-VIP-$part1-$part2"
    }

    fun reconcile(
        totalEarned: Int,
        redeemedPoints: Int,
        existingActiveCoupon: String?,
        redeemedCoupons: List<RedeemedLoyaltyCoupon>,
        couponGenerator: () -> String = ::generateUniqueCouponCode
    ): LoyaltyProgramState {
        val safeEarned = totalEarned.coerceAtLeast(0)
        val safeRedeemed = redeemedPoints.coerceIn(0, safeEarned)
        val balance = (safeEarned - safeRedeemed).coerceAtLeast(0)
        val activeCode = if (balance >= LOYALTY_GOAL) {
            existingActiveCoupon?.takeIf { it.isNotBlank() } ?: couponGenerator()
        } else {
            null
        }
        return LoyaltyProgramState(
            balance = balance,
            totalEarned = safeEarned,
            redeemedPoints = safeRedeemed,
            activeCouponCode = activeCode,
            redeemedCoupons = redeemedCoupons
        )
    }

    fun redeem(
        current: LoyaltyProgramState,
        nowMillis: Long = System.currentTimeMillis(),
        couponGenerator: () -> String = ::generateUniqueCouponCode
    ): Pair<LoyaltyProgramState, RedeemedLoyaltyCoupon?> {
        val couponCode = current.activeCouponCode?.takeIf { it.isNotBlank() } ?: return current to null
        if (current.balance < LOYALTY_GOAL) return current to null

        val redeemed = RedeemedLoyaltyCoupon(
            code = couponCode,
            redeemedAtMillis = nowMillis,
            pointsCost = LOYALTY_GOAL
        )
        val nextRedeemedPoints = current.redeemedPoints + LOYALTY_GOAL
        val nextList = (listOf(redeemed) + current.redeemedCoupons).take(15)
        val nextState = reconcile(
            totalEarned = current.totalEarned,
            redeemedPoints = nextRedeemedPoints,
            existingActiveCoupon = null,
            redeemedCoupons = nextList,
            couponGenerator = couponGenerator
        )
        return nextState to redeemed
    }
}

private val Context.pizzaClubDataStore by preferencesDataStore(name = "pizza_club_customer_orders")

class LocalOrderStore(private val context: Context) {
    private val ordersKey = stringPreferencesKey("saved_orders_json")
    private val pointsBalanceKey = intPreferencesKey("pizza_club_points_balance")
    private val redeemedPointsKey = intPreferencesKey("pizza_club_redeemed_points")
    private val activeLoyaltyCouponKey = stringPreferencesKey("pizza_club_active_loyalty_coupon")
    private val redeemedCouponsKey = stringPreferencesKey("pizza_club_redeemed_coupons_json")

    val savedOrdersFlow: Flow<List<LocalSavedOrder>> = context.pizzaClubDataStore.data.map { prefs ->
        val raw = prefs[ordersKey] ?: return@map emptyList()
        parseOrdersJson(raw)
    }

    val loyaltyStateFlow: Flow<LoyaltyProgramState> = context.pizzaClubDataStore.data.map { prefs ->
        val orders = parseOrdersJson(prefs[ordersKey] ?: "[]")
        val earned = orders.computePizzaClubPoints()
        val redeemedPts = prefs[redeemedPointsKey] ?: 0
        val activeCoupon = prefs[activeLoyaltyCouponKey]?.takeIf { it.isNotBlank() }
        val redeemedList = parseRedeemedCouponsJson(prefs[redeemedCouponsKey] ?: "[]")
        LoyaltyProgramCalculator.reconcile(
            totalEarned = earned,
            redeemedPoints = redeemedPts,
            existingActiveCoupon = activeCoupon,
            redeemedCoupons = redeemedList
        )
    }

    val pointsBalanceFlow: Flow<Int> = loyaltyStateFlow.map { it.balance }

    suspend fun saveOrder(order: LocalSavedOrder) {
        context.pizzaClubDataStore.edit { prefs ->
            val current = parseOrdersJson(prefs[ordersKey] ?: "[]").toMutableList()
            current.removeAll { it.orderId == order.orderId }
            current.add(0, order)
            val trimmed = current.take(25)
            prefs[ordersKey] = serializeOrdersJson(trimmed)
            val nextState = LoyaltyProgramCalculator.reconcile(
                totalEarned = trimmed.computePizzaClubPoints(),
                redeemedPoints = prefs[redeemedPointsKey] ?: 0,
                existingActiveCoupon = prefs[activeLoyaltyCouponKey],
                redeemedCoupons = parseRedeemedCouponsJson(prefs[redeemedCouponsKey] ?: "[]")
            )
            prefs[pointsBalanceKey] = nextState.balance
            if (nextState.activeCouponCode != null) {
                prefs[activeLoyaltyCouponKey] = nextState.activeCouponCode
            } else {
                prefs.remove(activeLoyaltyCouponKey)
            }
        }
    }

    suspend fun updateOrderStatus(orderId: String, status: String, whatsappUnlocked: Boolean): StatusTransition? {
        var transition: StatusTransition? = null
        context.pizzaClubDataStore.edit { prefs ->
            val current = parseOrdersJson(prefs[ordersKey] ?: "[]")
            val now = System.currentTimeMillis()
            val updated = current.map { existing ->
                if (existing.orderId == orderId) {
                    if (existing.status != status) {
                        transition = StatusTransition(
                            orderId = existing.orderId,
                            orderNumber = existing.orderNumber,
                            previousStatus = existing.status,
                            newStatus = status
                        )
                    }
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
            val nextState = LoyaltyProgramCalculator.reconcile(
                totalEarned = updated.computePizzaClubPoints(),
                redeemedPoints = prefs[redeemedPointsKey] ?: 0,
                existingActiveCoupon = prefs[activeLoyaltyCouponKey],
                redeemedCoupons = parseRedeemedCouponsJson(prefs[redeemedCouponsKey] ?: "[]")
            )
            prefs[pointsBalanceKey] = nextState.balance
            if (nextState.activeCouponCode != null) {
                prefs[activeLoyaltyCouponKey] = nextState.activeCouponCode
            } else {
                prefs.remove(activeLoyaltyCouponKey)
            }
        }
        return transition
    }

    suspend fun redeemLoyaltyReward(): RedeemedLoyaltyCoupon? {
        var redeemedResult: RedeemedLoyaltyCoupon? = null
        context.pizzaClubDataStore.edit { prefs ->
            val orders = parseOrdersJson(prefs[ordersKey] ?: "[]")
            val current = LoyaltyProgramCalculator.reconcile(
                totalEarned = orders.computePizzaClubPoints(),
                redeemedPoints = prefs[redeemedPointsKey] ?: 0,
                existingActiveCoupon = prefs[activeLoyaltyCouponKey],
                redeemedCoupons = parseRedeemedCouponsJson(prefs[redeemedCouponsKey] ?: "[]")
            )
            val (nextState, redeemed) = LoyaltyProgramCalculator.redeem(current)
            if (redeemed != null) {
                redeemedResult = redeemed
                prefs[redeemedPointsKey] = nextState.redeemedPoints
                prefs[pointsBalanceKey] = nextState.balance
                prefs[redeemedCouponsKey] = serializeRedeemedCouponsJson(nextState.redeemedCoupons)
                if (nextState.activeCouponCode != null) {
                    prefs[activeLoyaltyCouponKey] = nextState.activeCouponCode
                } else {
                    prefs.remove(activeLoyaltyCouponKey)
                }
            }
        }
        return redeemedResult
    }

    private fun List<LocalSavedOrder>.computePizzaClubPoints(): Int =
        filter { it.status != OrderStatus.CANCELLED.rawValue }
            .sumOf { order -> order.items.sumOf { it.quantity.toInt().coerceAtLeast(0) } }

    private fun serializeRedeemedCouponsJson(coupons: List<RedeemedLoyaltyCoupon>): String {
        val array = JSONArray()
        for (c in coupons) {
            array.put(
                JSONObject().apply {
                    put("code", c.code)
                    put("redeemedAtMillis", c.redeemedAtMillis)
                    put("pointsCost", c.pointsCost)
                }
            )
        }
        return array.toString()
    }

    private fun parseRedeemedCouponsJson(raw: String): List<RedeemedLoyaltyCoupon> {
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val code = obj.optString("code")
                    if (code.isNotBlank()) {
                        add(
                            RedeemedLoyaltyCoupon(
                                code = code,
                                redeemedAtMillis = obj.optLong("redeemedAtMillis", 0L),
                                pointsCost = obj.optInt("pointsCost", LoyaltyProgramCalculator.LOYALTY_GOAL)
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

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
