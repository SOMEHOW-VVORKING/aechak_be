package com.aechak.infra.persistence.order.claim

import com.aechak.domain.order.claim.Claim
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.claim.enums.RefundStatus
import com.aechak.domain.order.claim.enums.SellerApprovalStatus
import com.aechak.domain.order.claim.policy.ClaimProgressPolicy
import com.aechak.domain.order.claim.repository.ClaimRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

interface ClaimJpaRepository : JpaRepository<Claim, Long> {
    @Query("select count(c) > 0 from Claim c where c.orderId = :orderId and c.status in :statuses")
    fun existsByOrderIdAndStatusIn(
        @Param("orderId") orderId: Long,
        @Param("statuses") statuses: Collection<ClaimStatus>,
    ): Boolean

    // 품목과 주문품목까지 함께 끌어온다 — 목록 응답이 품목마다 추가 질의를 내지 않게.
    @Query(
        "select distinct c from Claim c " +
            "left join fetch c._items ci " +
            "left join fetch ci.orderItem " +
            "where c.publicId = :publicId",
    )
    fun findWithItemsByPublicId(
        @Param("publicId") publicId: String,
    ): Claim?

    @Query(
        "select distinct c from Claim c " +
            "left join fetch c._items ci " +
            "left join fetch ci.orderItem " +
            "where c.publicId = :publicId and c.sellerId = :sellerId",
    )
    fun findWithItemsByPublicIdAndSellerId(
        @Param("publicId") publicId: String,
        @Param("sellerId") sellerId: Long,
    ): Claim?

    @Query(
        "select distinct c from Claim c " +
            "left join fetch c._items ci " +
            "left join fetch ci.orderItem " +
            "where c.orderId = :orderId " +
            "order by c.id desc",
    )
    fun findAllWithItemsByOrderId(
        @Param("orderId") orderId: Long,
    ): List<Claim>

    @Query(
        "select count(ci) from Claim c join c._items ci " +
            "where ci.orderItem.id = :orderItemId and c.status in :statuses",
    )
    fun countHoldingByOrderItemId(
        @Param("orderItemId") orderItemId: Long,
        @Param("statuses") statuses: Collection<ClaimStatus>,
    ): Long

    // 상태 선두 인덱스(status, auto_approve_due_at)의 접두를 타고 승인 건만 훑는다
    @Query(
        "select c from Claim c " +
            "where c.status = :status and c.refundStatus = :refundStatus and c.claimType = :claimType " +
            "order by c.id asc",
    )
    fun findRefundPending(
        @Param("status") status: ClaimStatus,
        @Param("refundStatus") refundStatus: RefundStatus,
        @Param("claimType") claimType: ClaimType,
        pageable: Pageable,
    ): List<Claim>

    // 상태 선두 인덱스(status, auto_approve_due_at)를 상태 등가 + 기한 범위로 그대로 탄다
    @Query(
        "select c from Claim c " +
            "where c.status = :status and c.sellerApprovalStatus = :approvalStatus and c.claimType = :claimType " +
            "and c.autoApproveDueAt is not null and c.autoApproveDueAt < :dueBefore " +
            "order by c.autoApproveDueAt asc",
    )
    fun findAutoApprovable(
        @Param("status") status: ClaimStatus,
        @Param("approvalStatus") approvalStatus: SellerApprovalStatus,
        @Param("claimType") claimType: ClaimType,
        @Param("dueBefore") dueBefore: LocalDateTime,
        pageable: Pageable,
    ): List<Claim>
}

@Repository
class ClaimRepositoryAdapter(
    private val jpaRepository: ClaimJpaRepository,
) : ClaimRepository {
    override fun save(claim: Claim): Claim = jpaRepository.save(claim)

    override fun findByPublicId(publicId: String): Claim? = jpaRepository.findWithItemsByPublicId(publicId)

    override fun findByPublicIdAndSellerId(
        publicId: String,
        sellerId: Long,
    ): Claim? = jpaRepository.findWithItemsByPublicIdAndSellerId(publicId, sellerId)

    override fun findAllByOrderId(orderId: Long): List<Claim> = jpaRepository.findAllWithItemsByOrderId(orderId)

    override fun existsActiveByOrderItemId(orderItemId: Long): Boolean =
        jpaRepository.countHoldingByOrderItemId(orderItemId, ClaimStatus.HOLDING) > 0

    override fun findRefundPending(limit: Int): List<Claim> =
        jpaRepository.findRefundPending(
            status = ClaimStatus.APPROVED,
            refundStatus = RefundStatus.NONE,
            claimType = ClaimType.CANCEL,
            pageable = PageRequest.of(0, limit),
        )

    override fun findAutoApprovable(
        dueBefore: LocalDateTime,
        limit: Int,
    ): List<Claim> =
        jpaRepository.findAutoApprovable(
            status = ClaimStatus.CLAIM_REQUESTED,
            approvalStatus = SellerApprovalStatus.REQUESTED,
            claimType = ClaimType.CANCEL,
            dueBefore = dueBefore,
            pageable = PageRequest.of(0, limit),
        )

    override fun existsInProgressByOrderId(orderId: Long): Boolean =
        jpaRepository.existsByOrderIdAndStatusIn(orderId, ClaimProgressPolicy.IN_PROGRESS)
}
