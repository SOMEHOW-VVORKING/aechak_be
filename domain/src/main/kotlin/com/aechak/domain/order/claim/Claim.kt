package com.aechak.domain.order.claim

import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.order.claim.enums.ClaimFaultDecision
import com.aechak.domain.order.claim.enums.ClaimInterventionReason
import com.aechak.domain.order.claim.enums.ClaimReasonCode
import com.aechak.domain.order.claim.enums.ClaimSettlementStatus
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.claim.enums.RefundStatus
import com.aechak.domain.order.claim.enums.ReturnCostBearer
import com.aechak.domain.order.claim.enums.SellerApprovalStatus
import com.aechak.domain.order.error.OrderErrorCode
import com.aechak.domain.order.order.Order
import com.aechak.domain.support.AggregateRoot
import com.aechak.domain.support.Ulid
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version
import java.time.LocalDateTime

@Entity
@Table(
    name = "claims",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_claims_public_id", columnNames = ["public_id"]),
    ],
    indexes = [
        Index(name = "idx_claims_seller_id_status", columnList = "seller_id, status, id"),
        Index(name = "idx_claims_order_id", columnList = "order_id"),
        Index(name = "idx_claims_auto_approve_due_at", columnList = "status, auto_approve_due_at"),
    ],
)
class Claim protected constructor(
    claimType: ClaimType,
    sellerId: Long,
    orderId: Long,
) : AggregateRoot() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Column(nullable = false, updatable = false, length = 26)
    val publicId: String = Ulid.generate()

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val claimType: ClaimType = claimType

    /** 요청 시점 스냅샷 — claim_items를 거치는 2홉 조인 없이 셀러·주문 기준으로 조회한다 */
    @Column(nullable = false, updatable = false)
    val sellerId: Long = sellerId

    @Column(nullable = false, updatable = false)
    val orderId: Long = orderId

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    var reasonCode: ClaimReasonCode? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    var returnCostBearer: ReturnCostBearer? = null
        protected set

    @Column(length = 500)
    var reasonDetail: String? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: ClaimStatus = ClaimStatus.CLAIM_REQUESTED
        protected set

    @Column(length = 500)
    var rejectionReason: String? = null
        protected set

    @Column(length = 500)
    var adminReason: String? = null
        protected set

    /** 셀러 운영 메모. 어드민 개입 사유(adminReason)와 쓰는 주체가 다르다 */
    @Column(length = 500)
    var sellerMemo: String? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    var refundStatus: RefundStatus? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    var settlementStatus: ClaimSettlementStatus? = null
        protected set

    @Version
    @Column(nullable = false)
    var version: Int = 0
        protected set

    @Column
    var requestedAt: LocalDateTime? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    var sellerApprovalStatus: SellerApprovalStatus? = null
        protected set

    @Column
    var autoApproveDueAt: LocalDateTime? = null
        protected set

    @Column
    var clawbackDepositedAt: LocalDateTime? = null
        protected set

    @Column
    var approvedAt: LocalDateTime? = null
        protected set

    @Column
    var collectionStartedAt: LocalDateTime? = null
        protected set

    @Column
    var resolvedAt: LocalDateTime? = null
        protected set

    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "claim_id", nullable = false, updatable = false)
    private val _items: MutableList<ClaimItem> = mutableListOf()
    val items: List<ClaimItem> get() = _items.toList()

    /** 셀러 승인 — 취소는 즉시 승인으로 끝나고, 반품·교환은 회수를 기다린다. */
    fun approveBySeller(returnCostBearer: ReturnCostBearer?) {
        requireSellerDecisionPending()
        transitionTo(if (claimType == ClaimType.CANCEL) ClaimStatus.APPROVED else ClaimStatus.COLLECTING)
        sellerApprovalStatus = SellerApprovalStatus.APPROVED
        approvedAt = LocalDateTime.now()
        if (claimType != ClaimType.CANCEL) {
            this.returnCostBearer = returnCostBearer
        }
    }

    /** 셀러 거부 — 사유는 구매자에게 그대로 노출되므로 빈 값을 받지 않는다. */
    fun rejectBySeller(rejectionReason: String) {
        if (rejectionReason.isBlank()) {
            throw BusinessException(OrderErrorCode.CLAIM_REJECTION_REASON_REQUIRED)
        }
        requireSellerDecisionPending()
        transitionTo(ClaimStatus.REJECTED)
        sellerApprovalStatus = SellerApprovalStatus.REJECTED
        this.rejectionReason = rejectionReason.trim().take(REASON_MAX_LENGTH)
        resolvedAt = LocalDateTime.now()
    }

    /** 구매자 철회 — 셀러가 판단하기 전에만 거둘 수 있다. */
    fun withdrawByBuyer() {
        transitionTo(ClaimStatus.WITHDRAWN)
        resolvedAt = LocalDateTime.now()
    }

    /**
     * 운영자 개입 — 셀러 처리를 덮고 분쟁을 종결하며, 그 사실을 담은 개입 기록을 함께 낸다.
     * 셀러가 거부한 건도 이 경로로 뒤집힌다.
     *
     * 기록을 반환값으로 묶은 이유: 뒤집기와 기록이 따로면 "개입했는데 기록이 없다"가 호출 순서
     * 실수 하나로 생기고, 그 클레임은 운영자가 종결시켰다는 상태값만 남아 분쟁 근거가 되지 못한다.
     *
     * **돈은 움직이지 않는다.** 환불이 한 번이라도 움직인 건은 [isAdminOverridable]이 받지 않고,
     * 종결 상태(ADMIN_OVERRIDDEN)는 환불 집행 스캔 조건(APPROVED + RefundStatus.NONE)에서 벗어나
     * 뒤집은 뒤에도 집행이 걸리지 않는다. 결제 취소·환불액 재계산은 이 경로의 관심사가 아니다.
     *
     * 정하는 것은 반품비 부담 주체다 — 운영자가 명시하지 않으면 판정의 기본값을 쓰고,
     * 반품 발송이 없는 자리(취소 클레임·클레임 불가 판정)는 명시값까지 비운다.
     */
    fun overrideByAdmin(
        adminId: Long,
        faultDecision: ClaimFaultDecision,
        reason: ClaimInterventionReason,
        reasonDetail: String?,
        returnCostBearer: ReturnCostBearer?,
        internalMemo: String?,
    ): ClaimIntervention {
        if (!reason.isSelectableFor(faultDecision)) {
            throw BusinessException(OrderErrorCode.CLAIM_INTERVENTION_REASON_NOT_SELECTABLE)
        }
        if (reason.requiresDetail() && reasonDetail.isNullOrBlank()) {
            throw BusinessException(OrderErrorCode.CLAIM_INTERVENTION_REASON_DETAIL_REQUIRED)
        }
        if (!isAdminOverridable()) {
            throw BusinessException(OrderErrorCode.CLAIM_NOT_OVERRIDABLE)
        }
        val previousStatus = status
        val previousSellerApprovalStatus = sellerApprovalStatus
        val previousResolvedAt = resolvedAt
        transitionTo(ClaimStatus.ADMIN_OVERRIDDEN)
        // 구조화된 판정은 개입 기록(claim_interventions)이 들고, 이 칼럼은 그 기록에서 뜬 사람이 읽는 문장이다
        adminReason = reasonDetail?.trim()?.takeIf { it.isNotEmpty() }?.take(REASON_MAX_LENGTH) ?: reason.label
        // 반품 발송이 없는 자리(취소 클레임·클레임 불가 판정)에서는 명시값도 비운다 —
        // 부담 주체를 남기면 "요청이 성립하지 않았다"와 "누가 반품비를 낸다"가 한 기록에 함께 남는다
        this.returnCostBearer =
            if (claimType == ClaimType.CANCEL || !faultDecision.claimAccepted) {
                null
            } else {
                returnCostBearer ?: faultDecision.defaultReturnCostBearer
            }
        resolvedAt = LocalDateTime.now()
        return ClaimIntervention.record(
            claim = this,
            adminId = adminId,
            faultDecision = faultDecision,
            reason = reason,
            reasonDetail = reasonDetail,
            previousStatus = previousStatus,
            previousSellerApprovalStatus = previousSellerApprovalStatus,
            previousResolvedAt = previousResolvedAt,
            internalMemo = internalMemo,
        )
    }

    /**
     * 운영자가 개입할 수 있는지 — 상태 전이가 열려 있고 환불이 아직 한 번도 움직이지 않은 건만.
     *
     * 환불 축을 따로 보는 이유: 개입은 돈을 건드리지 않으므로, 이미 집행됐거나(DONE) 집행 중이거나
     * (REQUESTED) 실패로 멈춘(REFUND_FAILED) 건을 종결시키면 돌려줄 돈이 남은 채로 분쟁이 닫힌다.
     * 환불 실패 건의 운영 경로는 개입이 아니라 재집행([retryRefund])이다.
     *
     * 교환 종결을 셋째 축으로 보는 이유는 [isExchangeSettled]에 적혀 있다.
     */
    fun isAdminOverridable(): Boolean =
        status in ClaimStatus.ADMIN_OVERRIDABLE &&
            (refundStatus == null || refundStatus == RefundStatus.NONE) &&
            !isExchangeSettled()

    /**
     * 재발송으로 종결된 교환인지. 교환은 환불이 없어 종결돼도 상태가 APPROVED·환불 축이 NONE에
     * 머물고([completeExchange]가 종결 시각만 남긴다), 그래서 상태·환불 두 축만으로는 종결을 가릴 수 없다.
     * 이 축이 없으면 끝난 교환이 운영자의 작업 목록에 영구히 남고, 개입이 그 종결 시각을 덮는다.
     */
    fun isExchangeSettled(): Boolean = claimType == ClaimType.EXCHANGE && status == ClaimStatus.APPROVED && resolvedAt != null

    /**
     * 셀러 운영 메모 덮어쓰기. 공백만 보내면 삭제로 본다.
     * 상태 전이와 무관하므로 종결된 클레임에도 쓸 수 있다.
     */
    fun updateSellerMemo(memo: String?) {
        val normalized = memo?.trim()
        if (normalized != null && normalized.length > SELLER_MEMO_MAX_LENGTH) {
            throw BusinessException(CommonErrorCode.INVALID_REQUEST)
        }
        sellerMemo = normalized?.takeIf { it.isNotEmpty() }
    }

    /** 자동승인 대상인지 — 셀러가 판단하지 않은 채 응답 기한이 지난 건만. */
    fun isAutoApprovable(now: LocalDateTime): Boolean =
        status == ClaimStatus.CLAIM_REQUESTED &&
            sellerApprovalStatus == SellerApprovalStatus.REQUESTED &&
            autoApproveDueAt?.isBefore(now) == true

    /** 기한이 지난 셀러 미응답 건을 시스템이 승인한다 — 반품비 부담 주체는 정하지 않는다. */
    fun approveByTimeout() {
        approveBySeller(null)
    }

    /**
     * 회수 송장 등록 — 셀러가 승인해 회수 대기로 넘어온 반품·교환만. 상태는 그대로 두고 시작 시각만 남긴다.
     * 회수 완료 전까지 status가 COLLECTING에 머무는 것이 "물건을 받기 전에는 환불하지 않는다"의 근거다.
     */
    fun startCollection() {
        if (status != ClaimStatus.COLLECTING || collectionStartedAt != null) {
            throw BusinessException(OrderErrorCode.CLAIM_COLLECTION_NOT_STARTABLE)
        }
        collectionStartedAt = LocalDateTime.now()
    }

    /** 회수 완료 — 여기서야 승인이 끝난다. 반품 환불 집행은 이 전이 뒤에만 선점에 걸린다. */
    fun completeCollection() {
        if (status != ClaimStatus.COLLECTING || collectionStartedAt == null) {
            throw BusinessException(OrderErrorCode.CLAIM_COLLECTION_NOT_STARTABLE)
        }
        transitionTo(ClaimStatus.APPROVED)
    }

    /** 재발송 송장 등록 가능한지 — 교환이고 회수가 끝났고 아직 재발송하지 않은 건만. */
    fun isReshippable(): Boolean =
        claimType == ClaimType.EXCHANGE && status == ClaimStatus.APPROVED && collectionStartedAt != null && resolvedAt == null

    /** 교환 종결 — 환불이 없어 재발송이 종결 사건이다. */
    fun completeExchange() {
        if (!isReshippable()) {
            throw BusinessException(OrderErrorCode.CLAIM_RESHIP_NOT_ALLOWED)
        }
        resolvedAt = LocalDateTime.now()
    }

    /** 환불 집행 대상인지 — 셀러가 승인했고 아직 집행이 시작되지 않은 건만. */
    fun isRefundExecutable(): Boolean = status == ClaimStatus.APPROVED && refundStatus == RefundStatus.NONE

    /** 환불 집행 선점 표시. 이 1행이 집행의 멱등 게이트다. */
    fun markRefundRequested() {
        if (status != ClaimStatus.APPROVED || refundStatus != RefundStatus.NONE) {
            throw BusinessException(OrderErrorCode.INVALID_CLAIM_STATUS_TRANSITION)
        }
        refundStatus = RefundStatus.REQUESTED
    }

    fun markRefunded() {
        if (refundStatus != RefundStatus.REQUESTED) {
            throw BusinessException(OrderErrorCode.INVALID_CLAIM_STATUS_TRANSITION)
        }
        refundStatus = RefundStatus.DONE
        resolvedAt = LocalDateTime.now()
    }

    fun markRefundFailed() {
        if (refundStatus != RefundStatus.REQUESTED) {
            throw BusinessException(OrderErrorCode.INVALID_CLAIM_STATUS_TRANSITION)
        }
        transitionTo(ClaimStatus.REFUND_FAILED)
        refundStatus = RefundStatus.REFUND_FAILED
    }

    /** 실패한 환불을 다시 집행 대상으로 되돌린다 — 선점(markRefundRequested)을 다시 태워야 한다. */
    fun retryRefund() {
        if (refundStatus != RefundStatus.REFUND_FAILED) {
            throw BusinessException(OrderErrorCode.INVALID_CLAIM_STATUS_TRANSITION)
        }
        transitionTo(ClaimStatus.APPROVED)
        refundStatus = RefundStatus.NONE
    }

    /** 클레임이 대상으로 잡은 주문품목별 취소 수량. */
    fun claimedQuantityByOrderItemId(): Map<Long, Int> = _items.associate { it.orderItem.id to it.quantity }

    // 합산이 Long 범위를 넘기면 조용히 음수가 된 환불액을 내리는 대신 터뜨린다
    fun itemRefundTotal(): Long = _items.fold(0L) { sum, item -> Math.addExact(sum, item.refundAmount) }

    private fun requireSellerDecisionPending() {
        if (sellerApprovalStatus != SellerApprovalStatus.REQUESTED) {
            throw BusinessException(OrderErrorCode.CLAIM_ALREADY_RESOLVED)
        }
    }

    private fun transitionTo(next: ClaimStatus) {
        if (!status.canTransitionTo(next)) {
            throw BusinessException(OrderErrorCode.INVALID_CLAIM_STATUS_TRANSITION)
        }
        status = next
    }

    companion object {
        const val REASON_MAX_LENGTH = 500

        const val SELLER_MEMO_MAX_LENGTH = 500

        /**
         * 대상 품목은 이 주문 애그리거트가 들고 있는 품목이어야 한다 — 스냅샷(sellerId·orderId)과
         * 품목 경로가 다른 주문을 가리키면 셀러 목록과 상세가 갈린다. 그래서 ClaimItem은 조회한
         * 주문의 items에서 만들어 넘겨야 한다.
         */
        fun request(
            order: Order,
            claimType: ClaimType,
            reasonCode: ClaimReasonCode,
            reasonDetail: String?,
            items: List<ClaimItem>,
            autoApproveDueAt: LocalDateTime,
        ): Claim {
            require(order.id > 0L) { "클레임은 저장된 주문에만 걸 수 있습니다 — 스냅샷이 0으로 굳는다" }
            require(items.isNotEmpty()) { "클레임에는 대상 품목이 하나 이상 필요합니다" }
            val orderItems = order.items
            require(items.all { claimItem -> orderItems.any { it === claimItem.orderItem } }) {
                "클레임 품목은 모두 이 주문의 품목이어야 합니다 (orderId=${order.id})"
            }
            if (!reasonCode.isSelectableFor(claimType)) {
                throw BusinessException(OrderErrorCode.CLAIM_REASON_NOT_SELECTABLE)
            }
            if (reasonCode.requiresDetail() && reasonDetail.isNullOrBlank()) {
                throw BusinessException(OrderErrorCode.CLAIM_REASON_DETAIL_REQUIRED)
            }
            if (!claimType.isRequestableFrom(order.status)) {
                throw BusinessException(OrderErrorCode.CLAIM_NOT_REQUESTABLE)
            }
            return Claim(claimType, order.sellerId, order.id).apply {
                this.reasonCode = reasonCode
                this.reasonDetail = reasonDetail?.trim()?.take(REASON_MAX_LENGTH)
                sellerApprovalStatus = SellerApprovalStatus.REQUESTED
                refundStatus = RefundStatus.NONE
                settlementStatus = ClaimSettlementStatus.PENDING
                requestedAt = LocalDateTime.now()
                this.autoApproveDueAt = autoApproveDueAt
                items.forEach { _items += it }
            }
        }
    }
}
