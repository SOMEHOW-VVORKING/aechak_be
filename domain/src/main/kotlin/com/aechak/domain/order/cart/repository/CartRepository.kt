package com.aechak.domain.order.cart.repository

import com.aechak.domain.order.cart.Cart

interface CartRepository {
    fun save(cart: Cart): Cart

    fun findByBuyerId(buyerId: Long): Cart?

    fun findByBuyerIdForUpdate(buyerId: Long): Cart?

    fun flush()
}
