package com.aechak.seller.product.request

import com.aechak.application.product.usecase.command.UpdateProductCommand
import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.ProductImage
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

/** 이미지 키 목록은 전체 교체라 보낸 것이 곧 결과임. 옵션과 재고는 이 요청으로 바뀌지 않음. */
data class ProductUpdateRequest(
    val categoryId: Long,
    @field:NotBlank(message = "상품명은 필수입니다.")
    @field:Size(max = Product.NAME_MAX, message = "상품명은 {max}자 이하여야 합니다.")
    val productName: String,
    val description: String? = null,
    val regularPrice: Long,
    val discountPrice: Long? = null,
    val discountStartAt: LocalDateTime? = null,
    val discountEndAt: LocalDateTime? = null,
    @field:NotBlank(message = "대표 이미지 키는 필수입니다.")
    @field:Size(max = ProductImage.STORAGE_KEY_MAX, message = "이미지 키는 {max}자 이하여야 합니다.")
    val thumbnailImageKey: String,
    val additionalImageKeys: List<String>? = null,
    val detailImageKeys: List<String>? = null,
) {
    /** 여기서 거르지 않으면 길이 초과 키가 컬럼을 넘겨 저장 단계에서 500이 됨. */
    @get:AssertTrue(message = "이미지 키는 비울 수 없고 ${ProductImage.STORAGE_KEY_MAX}자 이하여야 합니다.")
    val imageKeysWellFormed: Boolean
        get() =
            (additionalImageKeys.orEmpty() + detailImageKeys.orEmpty())
                .all { it.isNotBlank() && it.length <= ProductImage.STORAGE_KEY_MAX }

    fun toCommand(
        sellerId: Long,
        productPublicId: String,
    ): UpdateProductCommand =
        UpdateProductCommand(
            sellerId = sellerId,
            productPublicId = productPublicId,
            categoryId = categoryId,
            productName = productName,
            description = description,
            regularPrice = regularPrice,
            discountPrice = discountPrice,
            discountStartAt = discountStartAt,
            discountEndAt = discountEndAt,
            thumbnailImageKey = thumbnailImageKey,
            additionalImageKeys = additionalImageKeys ?: emptyList(),
            detailImageKeys = detailImageKeys ?: emptyList(),
        )
}
