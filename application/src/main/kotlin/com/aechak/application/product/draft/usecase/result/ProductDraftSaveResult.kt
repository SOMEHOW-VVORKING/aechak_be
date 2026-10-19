package com.aechak.application.product.draft.usecase.result

import com.aechak.domain.product.draft.ProductDraft
import java.time.LocalDateTime

data class ProductDraftSaveResult(
    val draftId: String,
    val savedAt: LocalDateTime,
) {
    companion object {
        fun from(draft: ProductDraft): ProductDraftSaveResult = ProductDraftSaveResult(draft.publicId, draft.updatedAt)
    }
}
