package com.aechak.domain.order.claim

import com.aechak.domain.order.claim.enums.ClaimSettlementStatus
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.claim.enums.RefundStatus
import com.aechak.domain.order.claim.enums.ReturnCostBearer
import com.aechak.domain.order.claim.enums.SellerApprovalStatus
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

    companion object {
        fun request(
            claimType: ClaimType,
            order: Order,
            items: List<ClaimItem>,
        ): Claim =
            Claim(claimType, order.sellerId, order.id).apply {
                requestedAt = LocalDateTime.now()
                items.forEach { _items += it }
            }
    }
}
