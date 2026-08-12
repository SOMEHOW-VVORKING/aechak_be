package com.aechak.application.product.usecase

import com.aechak.application.product.usecase.command.RegisterProductCommand
import com.aechak.application.product.usecase.result.ProductRegisterResult

interface SellerProductUseCase {
    fun registerProduct(command: RegisterProductCommand): ProductRegisterResult
}
