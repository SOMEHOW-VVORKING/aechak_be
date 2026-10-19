package com.aechak.seller.product.response

import com.aechak.application.product.draft.usecase.result.ProductDraftSaveResult
import java.time.LocalDateTime

data class ProductDraftSaveResponse(
    /** product_drafts.public_id (ULID) */
    val draftId: String,
    val savedAt: LocalDateTime,
) {
    companion object {
        fun from(result: ProductDraftSaveResult): ProductDraftSaveResponse = ProductDraftSaveResponse(result.draftId, result.savedAt)
    }
}
