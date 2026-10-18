package com.aechak.domain.order.claim

import com.aechak.common.error.BusinessException
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

    @Column(length = 60)
    var reasonCode: String? = null
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

        /**
         * 대상 품목은 이 주문 애그리거트가 들고 있는 품목이어야 한다 — 스냅샷(sellerId·orderId)과
         * 품목 경로가 다른 주문을 가리키면 셀러 목록과 상세가 갈린다. 그래서 ClaimItem은 조회한
         * 주문의 items에서 만들어 넘겨야 한다.
         */
        fun request(
            order: Order,
            claimType: ClaimType,
            reasonCode: String,
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
            if (!claimType.isRequestableFrom(order.status)) {
                throw BusinessException(OrderErrorCode.CLAIM_NOT_REQUESTABLE)
            }
            return Claim(claimType, order.sellerId, order.id).apply {
                this.reasonCode = reasonCode.trim().take(REASON_CODE_MAX_LENGTH)
                this.reasonDetail = reasonDetail?.trim()?.take(REASON_MAX_LENGTH)
                sellerApprovalStatus = SellerApprovalStatus.REQUESTED
                refundStatus = RefundStatus.NONE
                settlementStatus = ClaimSettlementStatus.PENDING
                requestedAt = LocalDateTime.now()
                this.autoApproveDueAt = autoApproveDueAt
                items.forEach { _items += it }
            }
        }

        private const val REASON_CODE_MAX_LENGTH = 60
    }
}
