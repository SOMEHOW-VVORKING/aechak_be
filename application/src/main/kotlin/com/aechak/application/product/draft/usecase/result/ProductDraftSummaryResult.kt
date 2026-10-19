package com.aechak.application.product.draft.usecase.result

import com.aechak.domain.product.draft.ProductDraft
import java.time.LocalDateTime

data class ProductDraftSummaryResult(
    val draftId: String,
    val productName: String?,
    val savedAt: LocalDateTime,
) {
    companion object {
        fun from(draft: ProductDraft): ProductDraftSummaryResult =
            ProductDraftSummaryResult(
                draftId = draft.publicId,
                productName = draft.productName,
                savedAt = draft.savedAt,
            )
    }
}
