package com.aechak.application.product.product.usecase.command

import java.time.LocalDateTime

data class UpdateProductCommand(
    val sellerId: Long,
    val productPublicId: String,
    val categoryId: Long,
    val productName: String,
    val description: String?,
    val regularPrice: Long,
    val discountPrice: Long?,
    val discountStartAt: LocalDateTime?,
    val discountEndAt: LocalDateTime?,
    val thumbnailImageKey: String,
    val additionalImageKeys: List<String>,
    val detailImageKeys: List<String>,
)
