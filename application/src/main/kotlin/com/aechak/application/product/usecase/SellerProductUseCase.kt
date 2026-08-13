package com.aechak.application.product.usecase

import com.aechak.application.product.usecase.command.RegisterProductCommand
import com.aechak.application.product.usecase.command.UpdateProductCommand
import com.aechak.application.product.usecase.result.ProductRegisterResult
import com.aechak.application.product.usecase.result.ProductUpdateResult

interface SellerProductUseCase {
    fun registerProduct(command: RegisterProductCommand): ProductRegisterResult

    fun updateProduct(command: UpdateProductCommand): ProductUpdateResult
}
