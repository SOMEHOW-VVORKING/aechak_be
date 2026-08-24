package com.aechak.application.product.like.usecase.command

/** 찜 추가·취소 입력 */
data class ProductLikeCommand(
    val productPublicId: String,
    val userId: Long,
)
