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
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Service
class ProductDraftFacade(
    private val productDraftService: ProductDraftService,
    private val sellerUseCase: SellerUseCase,
) : ProductDraftUseCase {
    @Transactional(readOnly = true)
    override fun getMyDrafts(sellerId: Long): ProductDraftListResult {
        requireActiveSeller(sellerId)
        return ProductDraftListResult.from(productDraftService.getDrafts(sellerId, now()))
    }

    @Transactional(readOnly = true)
    override fun getMyDraft(
        sellerId: Long,
        draftId: String,
    ): ProductDraftResult {
        requireActiveSeller(sellerId)
        return ProductDraftResult.from(productDraftService.getDraft(sellerId, draftId, now()))
    }

    @Transactional
    override fun saveMyDraft(command: SaveProductDraftCommand): ProductDraftSaveResult {
        requireActiveSeller(command.sellerId)
        return ProductDraftSaveResult.from(productDraftService.save(command, now()))
    }

    @Transactional
    override fun deleteMyDraft(
        sellerId: Long,
        draftId: String,
    ) {
        requireActiveSeller(sellerId)
        productDraftService.delete(sellerId, draftId, now())
    }

    /**
     * saved_at은 DATETIME(6)이라 나노초를 넘기면 DB가 반올림한다 —
     * 그러면 저장 응답의 savedAt이 바로 뒤 조회와 어긋난다. 마이크로초로 끊어 둘을 같게 만든다.
     */
    private fun now(): LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS)

    /** 셀러가 아니면 seller BC가 10004로 거른다 — 여기 코드는 등록된 셀러의 상태 정책만 가린다 */
    private fun requireActiveSeller(sellerId: Long) {
        if (sellerUseCase.requireSellerStatus(sellerId) != SellerStatus.ACTIVE) {
            throw BusinessException(ProductErrorCode.PRODUCT_SELLER_NOT_ACTIVE)
        }
    }
}
