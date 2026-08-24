package com.aechak.application.product.product.usecase

import com.aechak.application.product.product.usecase.command.ChangeOptionCombinationCommand
import com.aechak.application.product.product.usecase.command.ChangeProductSaleStatusCommand
import com.aechak.application.product.product.usecase.command.RegisterProductCommand
import com.aechak.application.product.product.usecase.command.UpdateProductCommand
import com.aechak.application.product.product.usecase.result.OptionCombinationChangeResult
import com.aechak.application.product.product.usecase.result.ProductRegisterResult
import com.aechak.application.product.product.usecase.result.ProductSaleStatusChangeResult
import com.aechak.application.product.product.usecase.result.ProductUpdateResult

interface SellerProductUseCase {
    fun registerProduct(command: RegisterProductCommand): ProductRegisterResult

    fun updateProduct(command: UpdateProductCommand): ProductUpdateResult

    fun changeProductSaleStatus(command: ChangeProductSaleStatusCommand): ProductSaleStatusChangeResult

    fun changeOptionCombination(command: ChangeOptionCombinationCommand): OptionCombinationChangeResult
}
