package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.BusinessSettingsModel
import com.example.data.OrderLineItem
import com.example.data.OrderModel
import com.example.data.buildAdminCustomerThankYouMessage
import com.example.data.buildWhatsAppOrderMessage
import com.example.data.instagramProfileUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Pizza Club", appName)
  }

  @Test
  fun `whatsapp messages include thank you and treasure box qr teaser`() {
    val msg = buildWhatsAppOrderMessage(
      orderNumber = "PC-261124-1234",
      customerName = "Lucía Gómez",
      customerPhone = "0981123456",
      items = listOf(OrderLineItem("pepperoni", "Pepperoni", 50_000L, 1L, 50_000L)),
      preparationMode = "Lista para comer",
      fulfillmentType = "DELIVERY",
      neighborhood = "Área 1",
      streetAndNumber = "Av. del Lago 100",
      deliveryReference = "",
      googleMapsLink = "",
      pickupDate = "",
      pickupTime = "",
      desiredDate = "24/11/2026",
      additionalInstructions = "",
      totalAmountPyg = 50_000L,
      paymentStatusLabel = "Pago confirmado"
    )
    assertTrue(msg.contains("gracias por tu preferencia", ignoreCase = true))
    assertTrue(msg.contains("premio sorpresa", ignoreCase = true))
    assertTrue(msg.contains("QR"))

    val adminMsg = buildAdminCustomerThankYouMessage(
      order = OrderModel(orderNumber = "PC-261124-1234", customerName = "Lucía Gómez", totalAmountPyg = 50_000L),
      settings = BusinessSettingsModel()
    )
    assertTrue(adminMsg.contains("Pago Confirmado"))
    assertTrue(adminMsg.contains("@Pizza.Club1"))
    assertEquals("https://www.instagram.com/Pizza.Club1/", instagramProfileUrl("@Pizza.Club1"))
  }
}
