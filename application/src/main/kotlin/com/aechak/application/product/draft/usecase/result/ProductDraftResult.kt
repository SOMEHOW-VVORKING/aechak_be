package com.aechak.application.product.draft.usecase.result

import com.aechak.domain.product.draft.ProductDraft
import java.time.LocalDateTime

/** payload는 셀러가 보낸 중첩 입력 JSON 그대로다 — 모양을 아는 쪽이 boot라 여기서는 문자열로 지난다 */
data class ProductDraftResult(
    val draftId: String,
    val savedAt: LocalDateTime,
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
    companion object {
        fun from(draft: ProductDraft): ProductDraftResult =
            ProductDraftResult(
                draftId = draft.publicId,
                savedAt = draft.updatedAt,
                categoryId = draft.categoryId,
                productName = draft.productName,
                description = draft.description,
                regularPrice = draft.regularPrice,
                discountPrice = draft.discountPrice,
                discountStartAt = draft.discountStartAt,
                discountEndAt = draft.discountEndAt,
                thumbnailImageKey = draft.thumbnailImageKey,
                payload = draft.payload,
            )
    }
}
