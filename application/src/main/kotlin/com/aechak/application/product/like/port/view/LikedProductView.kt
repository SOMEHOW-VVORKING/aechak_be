package com.aechak.application.product.like.port.view

import com.aechak.application.product.product.port.view.ProductCatalogView

data class LikedProductView(
    val likeId: Long,
    val product: ProductCatalogView,
)
