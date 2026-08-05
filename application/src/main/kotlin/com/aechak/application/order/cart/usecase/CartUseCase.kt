package com.aechak.application.order.cart.usecase

import com.aechak.application.order.cart.usecase.command.AddCartItemCommand
import com.aechak.application.order.cart.usecase.result.AddCartItemResult

interface CartUseCase {
    fun addCartItem(command: AddCartItemCommand): AddCartItemResult
}
