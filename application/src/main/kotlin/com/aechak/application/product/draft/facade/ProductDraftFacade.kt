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
import com.aechak.domain.seller.seller.enums.SellerStatus
import com.aechak.domain.support.dbNow
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProductDraftFacade(
    private val productDraftService: ProductDraftService,
    private val sellerUseCase: SellerUseCase,
) : ProductDraftUseCase {
    @Transactional(readOnly = true)
    override fun getMyDrafts(sellerId: Long): ProductDraftListResult {
        requireActiveSeller(sellerId)
        return ProductDraftListResult.from(productDraftService.getDrafts(sellerId, dbNow()))
    }

    @Transactional(readOnly = true)
    override fun getMyDraft(
        sellerId: Long,
        draftId: String,
    ): ProductDraftResult {
        requireActiveSeller(sellerId)
        return ProductDraftResult.from(productDraftService.getDraft(sellerId, draftId, dbNow()))
    }

    @Transactional
    override fun saveMyDraft(command: SaveProductDraftCommand): ProductDraftSaveResult {
        requireActiveSeller(command.sellerId)
        return ProductDraftSaveResult.from(productDraftService.save(command, dbNow()))
    }

    @Transactional
    override fun deleteMyDraft(
        sellerId: Long,
        draftId: String,
    ) {
        requireActiveSeller(sellerId)
        productDraftService.delete(sellerId, draftId, dbNow())
    }

    /** 셀러가 아니면 seller BC가 10004로 거른다 — 여기 코드는 등록된 셀러의 상태 정책만 가린다 */
    private fun requireActiveSeller(sellerId: Long) {
        if (sellerUseCase.requireSellerStatus(sellerId) != SellerStatus.ACTIVE) {
            throw BusinessException(ProductErrorCode.PRODUCT_SELLER_NOT_ACTIVE)
        }
    }
}
