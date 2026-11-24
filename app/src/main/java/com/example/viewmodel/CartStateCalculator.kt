package com.example.viewmodel

import com.example.data.DefaultCatalogDefaults
import com.example.data.InventoryModel
import com.example.data.ProductModel

object CartStateCalculator {
    fun increment(
        currentCart: Map<String, Int>,
        productId: String,
        availableStock: Long
    ): Map<String, Int> {
        if (availableStock <= 0L) return currentCart - productId
        val prev = currentCart[productId] ?: 0
        return if (prev < availableStock) {
            currentCart + (productId to (prev + 1))
        } else {
            currentCart
        }
    }

    fun decrement(
        currentCart: Map<String, Int>,
        productId: String
    ): Map<String, Int> {
        val prev = currentCart[productId] ?: 0
        return if (prev <= 1) {
            currentCart - productId
        } else {
            currentCart + (productId to (prev - 1))
        }
    }

    fun buildCatalogItems(
        products: List<ProductModel>,
        inventoryMap: Map<String, InventoryModel>,
        cart: Map<String, Int>
    ): List<CatalogItemUi> {
        return products.filter { it.active }.map { product ->
            val inv = inventoryMap[product.id]
            val stock = (inv?.stock ?: DefaultCatalogDefaults.INITIAL_STOCK).coerceAtLeast(0L)
            val threshold = inv?.lowStockThreshold ?: 5L
            val clampedQty = (cart[product.id] ?: 0).coerceIn(0, stock.toInt())
            CatalogItemUi(
                product = product,
                stock = stock,
                lowStockThreshold = threshold,
                cartQuantity = clampedQty
            )
        }
    }

    fun computeSubtotalPyg(items: List<CatalogItemUi>): Long =
        items.sumOf { it.product.pricePyg * it.cartQuantity.toLong() }

    fun computeTotalCount(items: List<CatalogItemUi>): Int =
        items.sumOf { it.cartQuantity }
}
