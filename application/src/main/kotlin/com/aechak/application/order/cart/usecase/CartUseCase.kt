package com.aechak.application.order.cart.usecase

import com.aechak.application.order.cart.usecase.command.AddCartItemCommand
import com.aechak.application.order.cart.usecase.command.DeleteCartItemsCommand
import com.aechak.application.order.cart.usecase.command.UpdateCartItemCommand
import com.aechak.application.order.cart.usecase.result.AddCartItemResult
import com.aechak.application.order.cart.usecase.result.CartItemCountResult
import com.aechak.application.order.cart.usecase.result.CartResult
import com.aechak.application.order.cart.usecase.result.DeleteCartItemsResult
import com.aechak.application.order.cart.usecase.result.UpdateCartItemResult

interface CartUseCase {
    fun addCartItem(command: AddCartItemCommand): AddCartItemResult

    fun getCart(buyerId: Long): CartResult

    fun countCartItems(buyerId: Long): CartItemCountResult

    fun updateCartItem(command: UpdateCartItemCommand): UpdateCartItemResult

    fun deleteCartItems(command: DeleteCartItemsCommand): DeleteCartItemsResult
}
