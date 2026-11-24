package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.text.NumberFormat
import java.util.Locale

enum class PreparationMode(val label: String, val shortDesc: String) {
    READY_TO_EAT("Lista para comer", "Recién salida del horno, caliente y lista para servir."),
    READY_TO_BAKE("Lista para hornear en casa", "Pre-horneada artesanal para terminar en tu horno en 8 min.");

    companion object {
        fun fromRaw(raw: String?): PreparationMode =
            entries.firstOrNull { it.label == raw } ?: READY_TO_EAT
    }
}

enum class FulfillmentType(val rawValue: String, val label: String) {
    DELIVERY("DELIVERY", "Delivery a domicilio"),
    PICKUP("PICKUP", "Retiro en local");

    companion object {
        fun fromRaw(raw: String?): FulfillmentType =
            entries.firstOrNull { it.rawValue == raw } ?: DELIVERY
    }
}

enum class OrderStatus(
    val rawValue: String,
    val labelEs: String,
    val stepIndex: Int
) {
    PENDING_PAYMENT("PENDING_PAYMENT", "Pendiente de pago", 0),
    PAYMENT_CONFIRMED("PAYMENT_CONFIRMED", "Pago confirmado", 1),
    PREPARING("PREPARING", "En preparación", 2),
    READY_FOR_PICKUP("READY_FOR_PICKUP", "Lista para retiro", 3),
    OUT_FOR_DELIVERY("OUT_FOR_DELIVERY", "En camino", 3),
    COMPLETED("COMPLETED", "Completado", 4),
    CANCELLED("CANCELLED", "Cancelado", -1);

    companion object {
        fun fromRaw(raw: String?): OrderStatus =
            entries.firstOrNull { it.rawValue == raw } ?: PENDING_PAYMENT
    }
}

@Keep
@IgnoreExtraProperties
data class ProductModel(
    @DocumentId val id: String = "",
    val name: String = "",
    val description: String = "",
    val pricePyg: Long = 50_000L,
    val active: Boolean = true,
    val imageKey: String = "pepperoni",
    val sortOrder: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class InventoryModel(
    @DocumentId val id: String = "",
    val productName: String = "",
    val stock: Long = 20L,
    val lowStockThreshold: Long = 5L,
    val updatedBy: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class OrderLineItem(
    val productId: String = "",
    val productName: String = "",
    val unitPricePyg: Long = 0L,
    val quantity: Long = 0L,
    val lineTotalPyg: Long = 0L
) {
    fun toMap(): Map<String, Any> = mapOf(
        "productId" to productId,
        "productName" to productName,
        "unitPricePyg" to unitPricePyg,
        "quantity" to quantity,
        "lineTotalPyg" to lineTotalPyg
    )
}

@Keep
@IgnoreExtraProperties
data class OrderModel(
    @DocumentId val id: String = "",
    val orderNumber: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val desiredDate: String = "",
    val additionalInstructions: String? = null,
    val preparationMode: String = PreparationMode.READY_TO_EAT.label,
    val fulfillmentType: String = FulfillmentType.DELIVERY.rawValue,
    val neighborhood: String? = null,
    val streetAndNumber: String? = null,
    val deliveryReference: String? = null,
    val googleMapsLink: String? = null,
    val pickupDate: String? = null,
    val pickupTime: String? = null,
    val items: List<OrderLineItem> = emptyList(),
    val subtotalPyg: Long = 0L,
    val totalAmountPyg: Long = 0L,
    val status: String = OrderStatus.PENDING_PAYMENT.rawValue,
    val whatsappUnlocked: Boolean = false,
    val paymentMethod: String = "BANK_TRANSFER",
    val paymentBank: String = "Itaú Paraguay",
    val paymentAlias: String = "alejandromoises89@gmail.com",
    val paymentConfirmedAt: Timestamp? = null,
    val confirmedByAdminId: String? = null,
    val confirmedByAdminEmail: String? = null,
    val cancellationReason: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun statusEnum(): OrderStatus = OrderStatus.fromRaw(status)
    fun fulfillmentEnum(): FulfillmentType = FulfillmentType.fromRaw(fulfillmentType)
}

@Keep
@IgnoreExtraProperties
data class OrderStatusTrackerModel(
    @DocumentId val id: String = "",
    val orderNumber: String = "",
    val status: String = OrderStatus.PENDING_PAYMENT.rawValue,
    val whatsappUnlocked: Boolean = false,
    val totalAmountPyg: Long = 0L,
    val fulfillmentType: String = FulfillmentType.DELIVERY.rawValue,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun statusEnum(): OrderStatus = OrderStatus.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class BusinessSettingsModel(
    @DocumentId val id: String = "default",
    val brandName: String = "Pizza Club",
    val instagramHandle: String = "@Pizza.Club1",
    val whatsappNumber: String = "59593551936",
    val bankName: String = "Itaú Paraguay",
    val paymentAlias: String = "alejandromoises89@gmail.com",
    val deliveryCity: String = "Ciudad del Este",
    val currency: String = "PYG",
    val acceptingOrders: Boolean = true,
    val treasureCode: String = "PIZZACLUB-ORO",
    val treasureReward: String = "Dip Artesanal + 10% OFF en tu próxima pizza",
    val googleReviewUrl: String = "https://www.google.com/maps/search/?api=1&query=Pizza+Club+Ciudad+del+Este+Paraguay",
    val pwaInstallUrl: String = "https://gen-lang-client-0684931110.web.app",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PaymentAuditLogModel(
    @DocumentId val id: String = "",
    val orderId: String = "",
    val orderNumber: String = "",
    val adminId: String = "",
    val adminEmail: String = "",
    val action: String = "",
    val previousStatus: String = "",
    val newStatus: String = "",
    val amountPyg: Long = 0L,
    val note: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class LocalSavedOrder(
    val orderId: String,
    val orderNumber: String,
    val customerName: String,
    val customerPhone: String,
    val desiredDate: String,
    val additionalInstructions: String,
    val preparationMode: String,
    val fulfillmentType: String,
    val neighborhood: String,
    val streetAndNumber: String,
    val deliveryReference: String,
    val googleMapsLink: String,
    val pickupDate: String,
    val pickupTime: String,
    val items: List<OrderLineItem>,
    val totalAmountPyg: Long,
    val paymentBank: String,
    val paymentAlias: String,
    val whatsappNumber: String,
    val status: String,
    val whatsappUnlocked: Boolean,
    val createdAtMillis: Long,
    val stageUpdatedAtMillis: Long = createdAtMillis
)

object DefaultCatalogDefaults {
    const val BOOTSTRAP_ADMIN_EMAIL = "alejandromoises89@gmail.com"
    const val INITIAL_STOCK = 20L

    val defaultSettings = BusinessSettingsModel(
        id = "default",
        brandName = "Pizza Club",
        instagramHandle = "@Pizza.Club1",
        whatsappNumber = "59593551936",
        bankName = "Itaú Paraguay",
        paymentAlias = "alejandromoises89@gmail.com",
        deliveryCity = "Ciudad del Este",
        currency = "PYG",
        acceptingOrders = true,
        treasureCode = "PIZZACLUB-ORO",
        treasureReward = "Dip Artesanal + 10% OFF en tu próxima pizza",
        googleReviewUrl = "https://www.google.com/maps/search/?api=1&query=Pizza+Club+Ciudad+del+Este+Paraguay",
        pwaInstallUrl = "https://gen-lang-client-0684931110.web.app"
    )

    val defaultProducts = listOf(
        ProductModel(
            id = "pepperoni",
            name = "Pepperoni",
            description = "Salsa de tomate San Marzano, mozzarella fior di latte y pepperoni artesanal crocante.",
            pricePyg = 50_000L,
            active = true,
            imageKey = "pepperoni",
            sortOrder = 1L
        ),
        ProductModel(
            id = "mozzarella",
            name = "Mozzarella",
            description = "Clásica italiana con doble mozzarella cremosa, tomate natural, albahaca fresca y oliva.",
            pricePyg = 50_000L,
            active = true,
            imageKey = "mozzarella",
            sortOrder = 2L
        ),
        ProductModel(
            id = "four_cheeses",
            name = "Cuatro quesos",
            description = "Mozzarella, gorgonzola, parmesano estacionado y provolone con pimienta negra.",
            pricePyg = 50_000L,
            active = true,
            imageKey = "four_cheeses",
            sortOrder = 3L
        ),
        ProductModel(
            id = "chicken_catupiry",
            name = "Pollo Catupiri",
            description = "Pollo desmenuzado sazonado, queso cremoso Catupiry original, mozzarella y orégano.",
            pricePyg = 50_000L,
            active = true,
            imageKey = "chicken_catupiry",
            sortOrder = 4L
        )
    )

    val defaultInventory: List<InventoryModel> = defaultProducts.map { product ->
        InventoryModel(
            id = product.id,
            productName = product.name,
            stock = INITIAL_STOCK,
            lowStockThreshold = 5L,
            updatedBy = "system"
        )
    }
}

fun formatPyg(amount: Long): String {
    val formatter = NumberFormat.getIntegerInstance(Locale("es", "PY"))
    return "Gs. ${formatter.format(amount)}"
}

fun buildWhatsAppOrderMessage(
    orderNumber: String,
    customerName: String,
    customerPhone: String,
    items: List<OrderLineItem>,
    preparationMode: String,
    fulfillmentType: String,
    neighborhood: String,
    streetAndNumber: String,
    deliveryReference: String,
    googleMapsLink: String,
    pickupDate: String,
    pickupTime: String,
    desiredDate: String,
    additionalInstructions: String,
    totalAmountPyg: Long,
    paymentStatusLabel: String
): String {
    val itemsBlock = items.joinToString("\n") {
        "• ${it.quantity}x ${it.productName} (${formatPyg(it.lineTotalPyg)})"
    }
    val fulfillmentBlock = if (fulfillmentType == FulfillmentType.DELIVERY.rawValue) {
        buildString {
            appendLine("Modalidad: Delivery (Ciudad del Este)")
            appendLine("Barrio: $neighborhood")
            appendLine("Dirección: $streetAndNumber")
            if (deliveryReference.isNotBlank()) appendLine("Referencia: $deliveryReference")
            if (googleMapsLink.isNotBlank()) appendLine("Ubicación Maps: $googleMapsLink")
            append("Fecha deseada: $desiredDate")
        }
    } else {
        buildString {
            appendLine("Modalidad: Retiro en local")
            append("Fecha y hora de retiro: ${pickupDate.ifBlank { desiredDate }} $pickupTime".trim())
        }
    }

    return buildString {
        appendLine("*PIZZA CLUB - PEDIDO CONFIRMADO*")
        appendLine("Pedido N°: $orderNumber")
        appendLine("Estado de pago: $paymentStatusLabel")
        appendLine()
        appendLine("*Cliente:* $customerName")
        appendLine("*Teléfono:* $customerPhone")
        appendLine("*Preparación:* $preparationMode")
        appendLine()
        appendLine("*Detalle:*")
        appendLine(itemsBlock)
        appendLine()
        appendLine(fulfillmentBlock)
        if (additionalInstructions.isNotBlank()) {
            appendLine()
            appendLine("*Instrucciones:* $additionalInstructions")
        }
        appendLine()
        appendLine("*Total:* ${formatPyg(totalAmountPyg)}")
        appendLine()
        appendLine("✨ *¡Muchas gracias por tu preferencia!*")
        appendLine("Seguinos en Instagram: @Pizza.Club1")
        append("P.D.: Dentro de cada pizza hay un premio sorpresa 🎁 Escaneá el QR que viene en tu caja al recibir tu pedido.")
    }
}

fun buildAdminCustomerThankYouMessage(
    order: OrderModel,
    settings: BusinessSettingsModel
): String {
    return buildString {
        appendLine("🍕 *${settings.brandName} — ¡Pago Confirmado!*")
        appendLine("Hola ${order.customerName}, confirmamos tu transferencia para el pedido *${order.orderNumber}* (${formatPyg(order.totalAmountPyg)}).")
        appendLine()
        appendLine("¡Muchas gracias por tu preferencia! Ya estamos preparando tus pizzas artesanales.")
        appendLine()
        appendLine("📲 Seguinos en Instagram: ${settings.instagramHandle}")
        append("🎁 *P.D.:* Dentro de cada pizza hay un premio sorpresa. Escaneá el QR que viene en tu caja cuando la recibas.")
    }
}

fun instagramProfileUrl(handle: String): String {
    val clean = handle.trim().removePrefix("@").ifBlank { "Pizza.Club1" }
    return "https://www.instagram.com/$clean/"
}

fun formatOrderCountdownLabel(
    status: OrderStatus,
    fulfillmentType: FulfillmentType,
    stageStartedAtMillis: Long,
    nowMillis: Long
): String {
    val isDelivery = fulfillmentType == FulfillmentType.DELIVERY
    val durationMinutes = when (status) {
        OrderStatus.PAYMENT_CONFIRMED -> if (isDelivery) 35L else 25L
        OrderStatus.PREPARING -> 15L
        OrderStatus.OUT_FOR_DELIVERY -> 15L
        else -> 0L
    }

    if (durationMinutes == 0L) {
        return when (status) {
            OrderStatus.PENDING_PAYMENT -> if (isDelivery) {
                "Estimado tras confirmar: 35–45 min"
            } else {
                "Estimado tras confirmar: 20–25 min"
            }
            OrderStatus.READY_FOR_PICKUP -> "¡Listo para retirar ahora en el local!"
            OrderStatus.COMPLETED -> "Pedido entregado · ¡Buen provecho!"
            OrderStatus.CANCELLED -> "Pedido cancelado"
            else -> ""
        }
    }

    val safeStart = stageStartedAtMillis.coerceAtMost(nowMillis)
    val targetMillis = safeStart + durationMinutes * 60_000L
    val remainingSeconds = ((targetMillis - nowMillis) / 1_000L).coerceAtLeast(0L)
    if (remainingSeconds == 0L) {
        return when (status) {
            OrderStatus.OUT_FOR_DELIVERY -> "Llegando en instantes"
            OrderStatus.PREPARING -> "Saliendo del horno en instantes"
            else -> if (isDelivery) "Entrega en instantes" else "Listo de un momento a otro"
        }
    }

    val mins = remainingSeconds / 60L
    val secs = remainingSeconds % 60L
    val mmss = String.format(Locale.US, "%02d:%02d", mins, secs)
    return when (status) {
        OrderStatus.PAYMENT_CONFIRMED -> if (isDelivery) "Entrega aprox. en $mmss" else "Retiro aprox. en $mmss"
        OrderStatus.PREPARING -> "Horneando · Listo en $mmss"
        OrderStatus.OUT_FOR_DELIVERY -> "En camino · Llega en $mmss"
        else -> mmss
    }
}
