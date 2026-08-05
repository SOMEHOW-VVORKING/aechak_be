package com.aechak.application.order.cart.port.view

import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.seller.seller.enums.SellerStatus

data class CartCatalogItemView(
    val optionCombinationId: Long,
    val productPublicId: String,
    val stockQuantity: Int,
    val optionActive: Boolean,
    val saleStatus: SaleStatus,
    val sellerStatus: SellerStatus,
)
