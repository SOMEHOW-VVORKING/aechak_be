package com.aechak.application.product.draft.facade

import com.aechak.application.product.draft.service.ProductDraftService
import com.aechak.application.product.draft.usecase.ProductDraftUseCase
import com.aechak.application.product.draft.usecase.command.SaveProductDraftCommand
import com.aechak.application.product.draft.usecase.result.ProductDraftListResult
import com.aechak.application.product.draft.usecase.result.ProductDraftResult
import com.aechak.application.product.draft.usecase.result.ProductDraftSaveResult
import com.aechak.application.seller.usecase.SellerUseCase
import com.aechak.common.error.BusinessException
import com.aechak.domain.product.error.ProductErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class ProductDraftFacade(
    private val productDraftService: ProductDraftService,
    private val sellerUseCase: SellerUseCase,
) : ProductDraftUseCase {
    @Transactional(readOnly = true)
    override fun getMyDrafts(sellerId: Long): ProductDraftListResult {
        requireActiveSeller(sellerId)
        return ProductDraftListResult.from(productDraftService.getDrafts(sellerId, LocalDateTime.now()))
    }

    @Transactional(readOnly = true)
    override fun getMyDraft(
        sellerId: Long,
        draftId: String,
    ): ProductDraftResult {
        requireActiveSeller(sellerId)
        return ProductDraftResult.from(productDraftService.getDraft(sellerId, draftId, LocalDateTime.now()))
    }

    @Transactional
    override fun saveMyDraft(command: SaveProductDraftCommand): ProductDraftSaveResult {
        requireActiveSeller(command.sellerId)
        return ProductDraftSaveResult.from(productDraftService.save(command, LocalDateTime.now()))
    }

    @Transactional
    override fun deleteMyDraft(
        sellerId: Long,
        draftId: String,
    ) {
        requireActiveSeller(sellerId)
        productDraftService.delete(sellerId, draftId, LocalDateTime.now())
    }

    private fun requireActiveSeller(sellerId: Long) {
        if (!sellerUseCase.isActiveSeller(sellerId)) {
            throw BusinessException(ProductErrorCode.PRODUCT_SELLER_NOT_ACTIVE)
        }
    }
}
