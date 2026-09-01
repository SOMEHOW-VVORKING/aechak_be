package com.aechak.application.product.product.usecase

import com.aechak.application.product.product.usecase.command.RestoreStockCommand

interface ProductStockUseCase {
    fun restoreStock(command: RestoreStockCommand)
}
