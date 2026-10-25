package com.aechak.application.inspection.service

import com.aechak.application.inspection.port.ProductAppealTarget
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
    private val inspectionQuery: InspectionQueryService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 관리자가 봐야 함, 열린 행 없음 -> 새 행을 엶
     * 관리자가 봐야 함, 열린 행 있음 -> 그 행의 시각과 상품명만 갱신함
     * 관리자가 안 봐도 됨, 열린 행 있음 -> SUPERSEDED로 닫음
     *
     * [modelVerified]가 false인 통과는 관리자가 봐야 하는 쪽으로 간다. 증거 없는 통과로 열린
     * 행을 닫으면 사람이 봐야 할 상품이 조용히 큐에서 사라진다.
     */
    fun applyJudgment(
        productId: Long,
        judgment: InspectionJudgment,
        modelVerified: Boolean,
        judgedAt: LocalDateTime,
    ) {
        val outcome = productGate.applyJudgment(productId, judgment, modelVerified) ?: return
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
                pending.supersede(judgedAt)
                reviewQueue.save(pending)
                log.info("고친 내용이 통과해 관리자 검토 없이 닫았다. productId={} itemId={}", productId, pending.id)
            }
        }
    }

    fun approveByAdmin(
        productId: Long,
        now: LocalDateTime,
    ) {
        productGate.approveByAdmin(productId)
        val pending = getPending(productId)
        pending.approve(now)
        reviewQueue.save(pending)
    }

    fun rejectByAdmin(
        productId: Long,
        reason: String,
        now: LocalDateTime,
    ) {
        productGate.rejectByAdmin(productId)
        val pending = getPending(productId)
        pending.reject(reason, now)
        reviewQueue.save(pending)
    }

    /**
     * 셀러 이의를 접수함. 접수는 노출을 바꾸지 않음 — 바꾸면 이의가 곧 반려 해제 수단이 됨.
     *
     * 잠금 순서는 상품 행 -> 큐 행. 판정 반영·관리자 결정과 같은 순서라야 열린 행이 둘로 갈라지지 않음.
     * 열린 행이 있으면 그 행에 쓰고 없으면 새로 엶. 분기자는 열린 행 조회 하나임.
     */
    fun appealBySeller(
        productId: Long,
        reason: String,
        now: LocalDateTime,
    ): ProductReviewQueueItem {
        val target =
            productGate.lockForAppeal(productId)
                ?: throw BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND)
        val item = reviewQueue.findPendingByProductId(productId) ?: openForAppeal(productId, target, now)

        if (item.appealedAt != null) throw BusinessException(ProductErrorCode.INSPECTION_APPEAL_ALREADY_SUBMITTED)
        item.appeal(reason, now)
        val saved = reviewQueue.save(item)
        log.info("셀러 이의를 접수했다. productId={} itemId={}", productId, saved.id)
        return saved
    }

    /** 셀러별 열린 이의 건수. 폭주 상한의 분모임 */
    fun countOpenAppeals(sellerId: Long): Long = reviewQueue.countOpenAppealsByOwner(sellerId)

    /** 이 상품의 이의가 마지막으로 기각된 시각. 쿨다운의 기준임 */
    fun findLastAppealRejectedAt(productId: Long): LocalDateTime? = reviewQueue.findLastAppealRejectedAt(productId)

    /**
     * 열린 행이 없는 접수는 행을 새로 엶. 자격은 관리자 반려뿐임.
     *
     * 상품의 PENDING은 "첫 판정 대기"와 "사람 확인 대기"를 겸하고 둘을 가르는 것이 열린 행이라,
     * 노출 상태만 보고 받으면 등록 직후 모든 상품이 이의를 받게 됨.
     *
     * 새 행의 시각은 가장 최근 검수의 판정 시각임. 그 칼럼의 뜻이 "행을 열었거나 다시 연
     * 판정의 시각"이라서, 접수 시각으로 채우면 관리자 목록의 정렬 기준이 경로마다 갈림.
     */
    private fun openForAppeal(
        productId: Long,
        target: ProductAppealTarget,
        now: LocalDateTime,
    ): ProductReviewQueueItem {
        if (!target.adminRejected) throw BusinessException(ProductErrorCode.INSPECTION_APPEAL_NOT_ALLOWED)

        val blindedAt = inspectionQuery.findLatestProductInspection(productId)?.settledAt ?: now
        return ProductReviewQueueItem.openForProduct(productId, target.sellerId, target.productName, blindedAt)
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
