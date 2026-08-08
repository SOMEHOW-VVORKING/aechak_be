package com.aechak.application.order.cart.usecase

import com.aechak.application.order.cart.usecase.command.AddCartItemCommand
import com.aechak.application.order.cart.usecase.command.UpdateCartItemCommand
import com.aechak.application.order.cart.usecase.result.AddCartItemResult
import com.aechak.application.order.cart.usecase.result.CartResult
import com.aechak.application.order.cart.usecase.result.UpdateCartItemResult

interface CartUseCase {
    fun addCartItem(command: AddCartItemCommand): AddCartItemResult

    fun getCart(buyerId: Long): CartResult

    fun updateCartItem(command: UpdateCartItemCommand): UpdateCartItemResult
}
