package com.aechak.seller.product.response

import com.aechak.application.product.draft.usecase.result.ProductDraftListResult
import com.aechak.application.product.draft.usecase.result.ProductDraftSummaryResult
import java.time.LocalDateTime

data class ProductDraftListResponse(
    val items: List<Item>,
    val totalCount: Int,
) {
    data class Item(
        val draftId: String,
        val productName: String?,
        val savedAt: LocalDateTime,
    ) {
        companion object {
            fun from(result: ProductDraftSummaryResult): Item = Item(result.draftId, result.productName, result.savedAt)
        }
    }

    companion object {
        fun from(result: ProductDraftListResult): ProductDraftListResponse =
            ProductDraftListResponse(
                items = result.items.map(Item::from),
                totalCount = result.totalCount,
            )
    }
}
