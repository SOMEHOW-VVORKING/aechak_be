package com.aechak.domain.order.cart.repository

import com.aechak.domain.order.cart.Cart

interface CartRepository {
    fun existsByBuyerId(buyerId: Long): Boolean

    fun save(cart: Cart)

    fun findByBuyerIdForUpdate(buyerId: Long): Cart?

    fun findByBuyerIdWithItems(buyerId: Long): Cart?

    fun existsAnyItemById(cartItemIds: Collection<Long>): Boolean

    fun flush()
}
