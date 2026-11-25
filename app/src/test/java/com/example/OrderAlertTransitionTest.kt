package com.example

import com.example.data.OrderStatus
import com.example.notifications.OrderAlertPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderAlertTransitionTest {

    @Test
    fun alertsOnTransitionToPaymentConfirmed() {
        assertTrue(
            OrderAlertPolicy.shouldAlert(
                previousStatus = OrderStatus.PENDING_PAYMENT.rawValue,
                newStatus = OrderStatus.PAYMENT_CONFIRMED.rawValue
            )
        )
    }

    @Test
    fun alertsOnTransitionToOutForDelivery() {
        assertTrue(
            OrderAlertPolicy.shouldAlert(
                previousStatus = OrderStatus.PREPARING.rawValue,
                newStatus = OrderStatus.OUT_FOR_DELIVERY.rawValue
            )
        )
        assertTrue(
            OrderAlertPolicy.shouldAlert(
                previousStatus = OrderStatus.PAYMENT_CONFIRMED.rawValue,
                newStatus = OrderStatus.OUT_FOR_DELIVERY.rawValue
            )
        )
    }

    @Test
    fun ignoresDuplicateOrNonMilestoneTransitions() {
        assertFalse(
            OrderAlertPolicy.shouldAlert(
                previousStatus = OrderStatus.PAYMENT_CONFIRMED.rawValue,
                newStatus = OrderStatus.PAYMENT_CONFIRMED.rawValue
            )
        )
        assertFalse(
            OrderAlertPolicy.shouldAlert(
                previousStatus = OrderStatus.OUT_FOR_DELIVERY.rawValue,
                newStatus = OrderStatus.OUT_FOR_DELIVERY.rawValue
            )
        )
        assertFalse(
            OrderAlertPolicy.shouldAlert(
                previousStatus = OrderStatus.PAYMENT_CONFIRMED.rawValue,
                newStatus = OrderStatus.PREPARING.rawValue
            )
        )
        assertFalse(
            OrderAlertPolicy.shouldAlert(
                previousStatus = OrderStatus.OUT_FOR_DELIVERY.rawValue,
                newStatus = OrderStatus.COMPLETED.rawValue
            )
        )
        assertFalse(
            OrderAlertPolicy.shouldAlert(
                previousStatus = OrderStatus.PENDING_PAYMENT.rawValue,
                newStatus = OrderStatus.CANCELLED.rawValue
            )
        )
    }

    @Test
    fun buildsStableDeduplicationKey() {
        assertEquals(
            "ord_123:OUT_FOR_DELIVERY",
            OrderAlertPolicy.transitionKey("ord_123", OrderStatus.OUT_FOR_DELIVERY.rawValue)
        )
    }
}
