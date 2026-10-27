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
     * [inspectionId]는 이 판정을 실은 검수다. 상품 행을 잠근 뒤에 그것이 아직 그 상품의 최신
     * 검수인지 다시 보는 데 쓴다. 내용 지문으로 대조하지 않는 이유는 내용이 과거 값으로
     * 되돌아오면 같은 지문의 검수가 둘 있을 수 있기 때문이다.
     *
     * 큐 등재는 노출 게이트 스위치를 보지 않는다. 상품이 없거나 최신 판정이 아닐 때만 null이
     * 와서 돌아간다 — 스위치가 등재까지 끄면 꺼진 환경에 사람이 볼 목록이 영구히 비고 이의
     * 자격도 안 열린다.
     */
    fun applyJudgment(
        productId: Long,
        inspectionId: Long,
        judgment: InspectionJudgment,
        judgedAt: LocalDateTime,
        contentHash: String?,
    ) {
        val outcome =
            productGate.applyJudgment(
                productId,
                judgment,
                { isLatestJudgment(productId, inspectionId) },
                { isAdminRejected(productId) },
            ) ?: return
        val pending = reviewQueue.findPendingByProductIdForUpdate(productId)

        when {
            outcome.needsAdminReview && pending == null -> {
                reviewQueue.save(
                    ProductReviewQueueItem.openForProduct(productId, outcome.sellerId, outcome.productName, judgedAt),
                )
                log.info("상품을 관리자 검토 큐에 올렸다. productId={} judgment={}", productId, judgment)
            }

            outcome.needsAdminReview && pending != null -> {
                pending.refresh(outcome.productName, judgedAt, contentHash)
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
     * 이의 접수를 위해 상품 행을 잠금. 정책 조회보다 **먼저** 불러야 함.
     *
     * 폭주 상한과 쿨다운의 분모를 잠금 뒤에서 세게 하려는 것임. 잠금 전에 세면 같은 상품을
     * 두 번 누른 요청 둘이 서로의 커밋을 보지 못하고 같은 분모를 읽는다.
     */
    fun lockProductForAppeal(productId: Long): ProductAppealTarget =
        productGate.lockForAppeal(productId)
            ?: throw BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND)

    /**
     * 셀러 이의를 접수함. 접수는 노출을 바꾸지 않음 — 바꾸면 이의가 곧 반려 해제 수단이 됨.
     *
     * 잠금 순서는 상품 행 -> 큐 행. 판정 반영·관리자 결정과 같은 순서라야 열린 행이 둘로 갈라지지 않음.
     * 열린 행이 있으면 그 행에 쓰고 없으면 새로 엶. 분기자는 열린 행 조회 하나임.
     *
     * 그 조회는 **잠금 조회**임. 비잠금으로 읽으면 상품 행 잠금을 받아도 소용이 없음 —
     * 파사드 트랜잭션의 read view가 잠금보다 먼저 고정돼서, 먼저 커밋한 쪽이 넣은 행을 못 보고
     * 행을 또 열거나 이미 닫힌 행에 이의를 적는다. 닫힌 것이 보이면 열린 행 없음으로 떨어져
     * 자격(관리자 반려)을 다시 보게 되는 것이 올바른 답임.
     *
     * 지문은 **지금 그 행을 막고 있는 판정**에서 읽음. 판정 여부를 보지 않고 최신 검수를 쓰면,
     * 셀러가 내용을 고쳐 재검수가 아직 미판정인 사이에 접수한 이의가 그 고친 내용의 지문을 달고
     * 그 내용의 판정이 와도 무효가 되지 않음 — 관리자가 읽는 사유와 걸린 내용이 어긋남.
     *
     * [target]은 [lockProductForAppeal]이 돌려준 것이어야 함. 잠금을 쥔 채 받은 값이라야
     * 자격 판단과 쓰기가 같은 상태를 본다.
     */
    fun appealBySeller(
        productId: Long,
        target: ProductAppealTarget,
        reason: String,
        now: LocalDateTime,
    ): ProductReviewQueueItem {
        val blocking = inspectionQuery.findLatestSettledProductInspection(productId)
        val item =
            reviewQueue.findPendingByProductIdForUpdate(productId)
                ?: openForAppeal(productId, target, blocking?.settledAt ?: now)

        if (item.appealedAt != null) throw BusinessException(ProductErrorCode.INSPECTION_APPEAL_ALREADY_SUBMITTED)
        item.appeal(reason, now, blocking?.contentHash)
        val saved = reviewQueue.save(item)
        log.info("셀러 이의를 접수했다. productId={} itemId={} attempts={}", productId, saved.id, saved.appealAttemptCount)
        return saved
    }

    /**
     * 열린 행의 이의 이력. 행이 없으면 null — 접수가 그 행을 새로 연다는 뜻이다.
     *
     * 정책(중복·시도 횟수)은 호출부가 판단하고 여기는 그 입력만 읽는다. 쓰기와 같은 **잠금
     * 조회**로 읽는다 — 자격 판단과 쓰기가 같은 행 상태를 봐야 한다.
     */
    fun findOpenAppealState(productId: Long): OpenAppealState? =
        reviewQueue.findPendingByProductIdForUpdate(productId)?.let {
            OpenAppealState(alreadyAppealed = it.appealedAt != null, attemptCount = it.appealAttemptCount)
        }

    /** 셀러별 열린 이의 건수. 폭주 상한의 분모임 */
    fun countOpenAppeals(sellerId: Long): Long = reviewQueue.countOpenAppealsByOwner(sellerId)

    /** 이 상품의 이의가 마지막으로 기각된 시각. 쿨다운의 기준임 */
    fun findLastAppealRejectedAt(productId: Long): LocalDateTime? = reviewQueue.findLastAppealRejectedAt(productId)

    /**
     * 열린 행이 없는 접수는 행을 새로 엶. 자격은 관리자 반려뿐임.
     *
     * AI 반려는 같은 트랜잭션에서 행을 열기 때문에 이 길로 오지 않음. 여기로 오는 것은
     * 관리자가 반려해 그 행이 이미 닫힌 경우임. 노출 상태만 보고 받으면 AI가 반려한 상품이
     * 열린 행 없이도 새 행을 얻어, 관리자가 한 번도 보지 않은 반려에 이의가 붙음.
     *
     * 새 행의 시각은 가장 최근 검수의 판정 시각임. 그 칼럼의 뜻이 "행을 열었거나 다시 연
     * 판정의 시각"이라서, 접수 시각으로 채우면 관리자 목록의 정렬 기준이 경로마다 갈림.
     */
    private fun openForAppeal(
        productId: Long,
        target: ProductAppealTarget,
        blindedAt: LocalDateTime,
    ): ProductReviewQueueItem {
        if (!target.adminRejected) throw BusinessException(ProductErrorCode.INSPECTION_APPEAL_NOT_ALLOWED)

        return ProductReviewQueueItem.openForProduct(productId, target.sellerId, target.productName, blindedAt)
    }

    fun findRejected(productId: Long): List<ProductReviewQueueItem> = reviewQueue.findRejectedByProductId(productId)

    fun findPage(
        status: ReviewQueueStatus?,
        appealedOnly: Boolean,
        pageQuery: PageQuery,
    ): List<ProductReviewQueueRowView> = reviewQueueQuery.findPage(status, appealedOnly, pageQuery)

    fun count(
        status: ReviewQueueStatus?,
        appealedOnly: Boolean,
    ): Long = reviewQueueQuery.count(status, appealedOnly)

    fun getReviewTarget(productPublicId: String): ReviewTargetProductView =
        reviewQueueQuery.findReviewTarget(productPublicId)
            ?: throw BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND)

    private fun isLatestJudgment(
        productId: Long,
        inspectionId: Long,
    ): Boolean = inspectionQuery.findLatestProductInspection(productId)?.inspectionId == inspectionId

    private fun isAdminRejected(productId: Long): Boolean =
        reviewQueue.findLatestAdminDecisionByProductId(productId)?.status == ReviewQueueStatus.REJECTED

    // 상품 전이를 먼저 했어도 여기서 던지면 트랜잭션이 함께 롤백됨
    private fun getPending(productId: Long): ProductReviewQueueItem =
        reviewQueue.findPendingByProductIdForUpdate(productId)
            ?: throw BusinessException(ProductErrorCode.PRODUCT_INSPECTION_NOT_PENDING)
}
