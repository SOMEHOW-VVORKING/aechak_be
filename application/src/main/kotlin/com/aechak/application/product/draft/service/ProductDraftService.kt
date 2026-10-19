package com.aechak.application.product.draft.service

import com.aechak.application.product.draft.usecase.command.SaveProductDraftCommand
import com.aechak.common.error.BusinessException
import com.aechak.domain.product.draft.ProductDraft
import com.aechak.domain.product.draft.repository.ProductDraftRepository
import com.aechak.domain.product.error.ProductErrorCode
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class ProductDraftService(
    private val productDraftRepository: ProductDraftRepository,
) {
    fun getDrafts(
        sellerId: Long,
        now: LocalDateTime,
    ): List<ProductDraft> = productDraftRepository.findAllLive(sellerId, liveSince(now))

    fun getDraft(
        sellerId: Long,
        publicId: String,
        now: LocalDateTime,
    ): ProductDraft = findLiveOrThrow(sellerId, publicId, now)

    /**
     * 신규는 상한을 보고, 덮어쓰기는 상한을 보지 않는다.
     * 저장 때마다 보관 기간이 지난 초안을 먼저 치워 상한 계산이 살아 있는 초안만 세게 한다.
     */
    fun save(
        command: SaveProductDraftCommand,
        now: LocalDateTime,
    ): ProductDraft {
        productDraftRepository.deleteExpired(command.sellerId, liveSince(now))
        val draftId = command.draftId
        if (draftId == null) {
            if (productDraftRepository.countLive(command.sellerId, liveSince(now)) >= MAX_PRODUCT_DRAFTS) {
                throw BusinessException(ProductErrorCode.PRODUCT_DRAFT_LIMIT_EXCEEDED)
            }
            return productDraftRepository.save(command.toEntity(now))
        }
        return findLiveOrThrow(command.sellerId, draftId, now).also { command.applyTo(it, now) }
    }

    fun delete(
        sellerId: Long,
        publicId: String,
        now: LocalDateTime,
    ) {
        if (productDraftRepository.deleteLive(publicId, sellerId, liveSince(now)) == 0) {
            throw BusinessException(ProductErrorCode.PRODUCT_DRAFT_NOT_FOUND)
        }
    }

    /** 남의 초안과 없는 초안, 보관 기간이 지난 초안을 모두 같은 404로 묶는다 */
    private fun findLiveOrThrow(
        sellerId: Long,
        publicId: String,
        now: LocalDateTime,
    ): ProductDraft =
        productDraftRepository.findLive(publicId, sellerId, liveSince(now))
            ?: throw BusinessException(ProductErrorCode.PRODUCT_DRAFT_NOT_FOUND)

    private fun liveSince(now: LocalDateTime): LocalDateTime = now.minusDays(RETENTION_DAYS)

    companion object {
        /** 셀러당 보유 가능한 초안 수 */
        const val MAX_PRODUCT_DRAFTS = 10

        /** 마지막 저장 이후 이 기간이 지난 초안은 조회에서 사라지고 다음 저장 때 지워진다 */
        const val RETENTION_DAYS = 30L
    }
}
