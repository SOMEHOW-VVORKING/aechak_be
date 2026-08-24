package com.aechak.api.order.cart.request

import com.aechak.domain.order.cart.CartItem

object CartItemConstraints {
    const val QUANTITY_MIN = CartItem.MIN_QUANTITY

    const val QUANTITY_MAX = CartItem.MAX_QUANTITY
}
