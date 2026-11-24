package com.example

import com.example.data.DefaultCatalogDefaults
import com.example.data.InventoryModel
import com.example.viewmodel.CartStateCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class CartStateCalculatorTest {

    @Test
    fun multiFlavorSelection_calculatesSubtotalAndTotalCountCorrectly() {
        val products = DefaultCatalogDefaults.defaultProducts
        val invMap = DefaultCatalogDefaults.defaultInventory.associateBy { it.id }

        var cart = emptyMap<String, Int>()
        cart = CartStateCalculator.increment(cart, "pepperoni", 20L)
        cart = CartStateCalculator.increment(cart, "pepperoni", 20L)
        cart = CartStateCalculator.increment(cart, "four_cheeses", 20L)

        val items = CartStateCalculator.buildCatalogItems(products, invMap, cart)
        assertEquals(3, CartStateCalculator.computeTotalCount(items))
        assertEquals(150_000L, CartStateCalculator.computeSubtotalPyg(items))

        cart = CartStateCalculator.decrement(cart, "pepperoni")
        val updatedItems = CartStateCalculator.buildCatalogItems(products, invMap, cart)
        assertEquals(2, CartStateCalculator.computeTotalCount(updatedItems))
        assertEquals(100_000L, CartStateCalculator.computeSubtotalPyg(updatedItems))
    }

    @Test
    fun increment_preventsExceedingAvailableStock_andZeroStockIsRejected() {
        var cart = emptyMap<String, Int>()
        // Try to add 3 units when only 2 are in stock
        cart = CartStateCalculator.increment(cart, "mozzarella", 2L)
        cart = CartStateCalculator.increment(cart, "mozzarella", 2L)
        cart = CartStateCalculator.increment(cart, "mozzarella", 2L)
        assertEquals(2, cart["mozzarella"])

        // Out-of-stock item cannot be added
        cart = CartStateCalculator.increment(cart, "chicken_catupiry", 0L)
        assertEquals(null, cart["chicken_catupiry"])
    }

    @Test
    fun buildCatalogItems_clampsQuantityIfStockDropsBelowCart() {
        val products = DefaultCatalogDefaults.defaultProducts
        val invMap = mapOf(
            "pepperoni" to InventoryModel(id = "pepperoni", productName = "Pepperoni", stock = 1L)
        )
        val cart = mapOf("pepperoni" to 5)

        val items = CartStateCalculator.buildCatalogItems(products, invMap, cart)
        val pepperoni = items.first { it.product.id == "pepperoni" }
        assertEquals(1, pepperoni.cartQuantity)
        assertEquals(50_000L, CartStateCalculator.computeSubtotalPyg(items))
    }
}
