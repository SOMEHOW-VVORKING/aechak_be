package com.aechak.application.inspection.service

import com.aechak.application.inspection.port.ProductInspectionGate
import com.aechak.application.inspection.port.ProductReviewQueueQueryPort
import com.aechak.application.inspection.port.view.ProductReviewQueueRowView
import com.aechak.application.inspection.port.view.ReviewTargetProductView
import com.aechak.application.support.PageQuery
import com.aechak.common.error.BusinessException
import com.aechak.domain.inspection.ProductReviewQueueItem
import com.aechak.domain.inspection.enums.InspectionJudgment
import com.aechak.domain.inspection.enums.ReviewQueueStatus
import com.aechak.domain.inspection.repository.ProductReviewQueueRepository
import com.aechak.domain.product.error.ProductErrorCode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 판정과 관리자 결정을 상품 노출과 관리자 검토 큐에 함께 반영함.
 *
 * 트랜잭션 애너테이션이 없어 호출부의 트랜잭션 안에서 돌아야 함. 매번 상품 행을 먼저 잠근 뒤 큐 행을 찾으므로
 * 판정 반영과 관리자 결정이 겹쳐도 열린 행이 둘로 갈라지지 않음.
 */
@Service
class ProductReviewQueueService(
    private val reviewQueue: ProductReviewQueueRepository,
    private val reviewQueueQuery: ProductReviewQueueQueryPort,
    private val productGate: ProductInspectionGate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 관리자가 봐야 함, 열린 행 없음 -> 새 행을 엶
     * 관리자가 봐야 함, 열린 행 있음 -> 그 행의 시각과 상품명만 갱신함
     * 관리자가 안 봐도 됨, 열린 행 있음 -> SUPERSEDED로 닫음
     */
    fun applyJudgment(
        productId: Long,
        judgment: InspectionJudgment,
        judgedAt: LocalDateTime,
    ) {
        val outcome = productGate.applyJudgment(productId, judgment) ?: return
        val pending = reviewQueue.findPendingByProductId(productId)

        when {
            outcome.needsAdminReview && pending == null -> {
                reviewQueue.save(
                    ProductReviewQueueItem.openForProduct(productId, outcome.sellerId, outcome.productName, judgedAt),
                )
                log.info("상품을 관리자 검토 큐에 올렸다. productId={} judgment={}", productId, judgment)
            }

            outcome.needsAdminReview && pending != null -> {
                pending.refresh(outcome.productName, judgedAt)
                reviewQueue.save(pending)
            }

            pending != null -> {
                pending.supersede()
                reviewQueue.save(pending)
                log.info("고친 내용이 통과해 관리자 검토 없이 닫았다. productId={} itemId={}", productId, pending.id)
            }
        }
    }

    fun approveByAdmin(productId: Long) {
        productGate.approveByAdmin(productId)
        val pending = getPending(productId)
        pending.approve()
        reviewQueue.save(pending)
    }

    fun rejectByAdmin(
        productId: Long,
        reason: String,
    ) {
        productGate.rejectByAdmin(productId)
        val pending = getPending(productId)
        pending.reject(reason)
        reviewQueue.save(pending)
    }

    fun findRejected(productId: Long): List<ProductReviewQueueItem> = reviewQueue.findRejectedByProductId(productId)

    fun findPage(
        status: ReviewQueueStatus?,
        pageQuery: PageQuery,
    ): List<ProductReviewQueueRowView> = reviewQueueQuery.findPage(status, pageQuery)

    fun count(status: ReviewQueueStatus?): Long = reviewQueueQuery.count(status)

    fun getReviewTarget(productPublicId: String): ReviewTargetProductView =
        reviewQueueQuery.findReviewTarget(productPublicId)
            ?: throw BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND)

    // 상품 전이를 먼저 했어도 여기서 던지면 트랜잭션이 함께 롤백됨
    private fun getPending(productId: Long): ProductReviewQueueItem =
        reviewQueue.findPendingByProductId(productId)
            ?: throw BusinessException(ProductErrorCode.PRODUCT_INSPECTION_NOT_PENDING)
}
