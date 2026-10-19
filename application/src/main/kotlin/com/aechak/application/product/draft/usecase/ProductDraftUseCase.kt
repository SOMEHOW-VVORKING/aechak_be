package com.aechak.application.product.draft.usecase

import com.aechak.application.product.draft.usecase.command.SaveProductDraftCommand
import com.aechak.application.product.draft.usecase.result.ProductDraftListResult
import com.aechak.application.product.draft.usecase.result.ProductDraftResult
import com.aechak.application.product.draft.usecase.result.ProductDraftSaveResult

interface ProductDraftUseCase {
    fun getMyDrafts(sellerId: Long): ProductDraftListResult

    fun getMyDraft(
        sellerId: Long,
        draftId: String,
    ): ProductDraftResult

    fun saveMyDraft(command: SaveProductDraftCommand): ProductDraftSaveResult

    fun deleteMyDraft(
        sellerId: Long,
        draftId: String,
    )
}
