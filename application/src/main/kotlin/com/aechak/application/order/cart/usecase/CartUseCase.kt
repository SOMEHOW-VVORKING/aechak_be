package com.aechak.application.order.cart.usecase

import com.aechak.application.order.cart.usecase.command.AddCartItemCommand
import com.aechak.application.order.cart.usecase.result.AddCartItemResult
import com.aechak.application.order.cart.usecase.result.CartResult

interface CartUseCase {
    fun addCartItem(command: AddCartItemCommand): AddCartItemResult

    fun getCart(buyerId: Long): CartResult
}
