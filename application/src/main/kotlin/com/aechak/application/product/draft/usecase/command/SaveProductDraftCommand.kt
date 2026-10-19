package com.aechak.application.product.draft.usecase.command

import com.aechak.domain.product.draft.ProductDraft
import java.time.LocalDateTime

/**
 * 상품 임시저장 입력. draftId가 null이면 새 초안, 있으면 그 초안을 덮어쓴다.
 * 전 필드가 nullable인 이유는 폼을 채우는 중에도 저장이 되어야 하기 때문이다.
 */
data class SaveProductDraftCommand(
    val sellerId: Long,
    val draftId: String?,
    val categoryId: Long?,
    val productName: String?,
    val description: String?,
    val regularPrice: Long?,
    val discountPrice: Long?,
    val discountStartAt: LocalDateTime?,
    val discountEndAt: LocalDateTime?,
    val thumbnailImageKey: String?,
    val payload: String,
) {
    fun toEntity(): ProductDraft =
        ProductDraft.of(
            sellerId = sellerId,
            categoryId = categoryId,
            productName = productName,
            description = description,
            regularPrice = regularPrice,
            discountPrice = discountPrice,
            discountStartAt = discountStartAt,
            discountEndAt = discountEndAt,
            thumbnailImageKey = thumbnailImageKey,
            payload = payload,
        )

    fun applyTo(draft: ProductDraft) {
        draft.overwrite(
            categoryId = categoryId,
            productName = productName,
            description = description,
            regularPrice = regularPrice,
            discountPrice = discountPrice,
            discountStartAt = discountStartAt,
            discountEndAt = discountEndAt,
            thumbnailImageKey = thumbnailImageKey,
            payload = payload,
        )
    }
}
