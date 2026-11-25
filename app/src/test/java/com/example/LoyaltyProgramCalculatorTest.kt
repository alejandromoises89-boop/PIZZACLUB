package com.example

import com.example.data.LoyaltyProgramCalculator
import com.example.data.LoyaltyProgramState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoyaltyProgramCalculatorTest {

    @Test
    fun generatesUniqueCouponAutomaticallyAtTenPoints() {
        val belowGoal = LoyaltyProgramCalculator.reconcile(
            totalEarned = 9,
            redeemedPoints = 0,
            existingActiveCoupon = null,
            redeemedCoupons = emptyList()
        )
        assertNull(belowGoal.activeCouponCode)
        assertEquals(9, belowGoal.balance)

        val atGoal = LoyaltyProgramCalculator.reconcile(
            totalEarned = 10,
            redeemedPoints = 0,
            existingActiveCoupon = null,
            redeemedCoupons = emptyList(),
            couponGenerator = { "PC-VIP-ABCD-1234" }
        )
        assertEquals(10, atGoal.balance)
        assertEquals("PC-VIP-ABCD-1234", atGoal.activeCouponCode)
    }

    @Test
    fun keepsExistingCouponAndRedeemsTenPointsCleanly() {
        val initial = LoyaltyProgramState(
            balance = 12,
            totalEarned = 12,
            redeemedPoints = 0,
            activeCouponCode = "PC-VIP-9988-7766",
            redeemedCoupons = emptyList()
        )
        val (afterRedeem, redeemed) = LoyaltyProgramCalculator.redeem(
            current = initial,
            nowMillis = 123456L
        )
        assertNotNull(redeemed)
        assertEquals("PC-VIP-9988-7766", redeemed?.code)
        assertEquals(2, afterRedeem.balance)
        assertEquals(10, afterRedeem.redeemedPoints)
        assertNull(afterRedeem.activeCouponCode)
        assertEquals(1, afterRedeem.redeemedCoupons.size)
    }

    @Test
    fun generatedCouponCodesAreUniqueAndFormatted() {
        val codes = (1..20).map { LoyaltyProgramCalculator.generateUniqueCouponCode() }.toSet()
        assertEquals(20, codes.size)
        assertTrue(codes.all { it.startsWith("PC-VIP-") && it.length == 16 })
    }
}
