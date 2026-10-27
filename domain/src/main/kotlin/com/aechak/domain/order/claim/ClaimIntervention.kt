package com.aechak.domain.order.claim

import com.aechak.domain.order.claim.enums.ClaimFaultDecision
import com.aechak.domain.order.claim.enums.ClaimInterventionReason
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ReturnCostBearer
import com.aechak.domain.order.claim.enums.SellerApprovalStatus
import com.aechak.domain.support.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

/**
 * 운영자 개입 기록. 누가·언제·무엇을 어떤 근거로 뒤집었는지를 남긴다 — 이 기록이 없으면
 * 뒤집힌 클레임은 "운영자가 종결한 건"이라는 상태값만 남아 분쟁에서 근거로 쓸 수 없다.
 *
 * 한 클레임에 행 하나다(claim_id UNIQUE). 개입은 클레임을 종결 상태로 보내고 종결 상태에서는
 * 다시 개입할 수 없으므로 두 번째 행이 생길 경로가 없고, 제약이 그것을 DB에서도 못박는다.
 *
 * 모든 칼럼이 updatable=false인 이유: 개입 기록은 사후 수정 대상이 아니다. 판정을 바꿔야 하면
 * 그 사실이 또 하나의 사건이므로 이 행을 고치는 것으로는 표현되지 않는다.
 */
@Entity
@Table(
    name = "claim_interventions",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_claim_interventions_claim_id", columnNames = ["claim_id"]),
    ],
    indexes = [
        Index(name = "ix_claim_interventions_admin", columnList = "admin_id, id"),
    ],
)
class ClaimIntervention protected constructor(
    claimId: Long,
    adminId: Long,
    faultDecision: ClaimFaultDecision,
    reason: ClaimInterventionReason,
    reasonDetail: String?,
    returnCostBearer: ReturnCostBearer?,
    previousStatus: ClaimStatus,
    previousSellerApprovalStatus: SellerApprovalStatus?,
    previousResolvedAt: LocalDateTime?,
    internalMemo: String?,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Column(nullable = false, updatable = false)
    val claimId: Long = claimId

    /** 개입한 운영자(users.id). 사람을 특정할 수 없는 기록은 기록이 아니다. */
    @Column(nullable = false, updatable = false)
    val adminId: Long = adminId

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    val faultDecision: ClaimFaultDecision = faultDecision

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 40)
    val reason: ClaimInterventionReason = reason

    /** 양쪽에 전달하는 판정 사유 본문. 고정 어휘만으로 충분하면 비어 있다. */
    @Column(updatable = false, length = 500)
    val reasonDetail: String? = reasonDetail?.trim()?.takeIf { it.isNotEmpty() }?.take(REASON_DETAIL_MAX_LENGTH)

    /** 개입이 정한 반품비 부담 주체. 취소 클레임과 클레임 불가 판정에서는 비어 있다. */
    @Enumerated(EnumType.STRING)
    @Column(updatable = false, length = 30)
    val returnCostBearer: ReturnCostBearer? = returnCostBearer

    /** 뒤집기 직전의 클레임 상태 — 무엇을 덮었는지가 이 값이다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    val previousStatus: ClaimStatus = previousStatus

    /** 뒤집기 직전의 셀러 판단. 셀러가 거부한 것을 뒤집었는지 미응답을 대신 끝냈는지를 가른다. */
    @Enumerated(EnumType.STRING)
    @Column(updatable = false, length = 30)
    val previousSellerApprovalStatus: SellerApprovalStatus? = previousSellerApprovalStatus

    /**
     * 뒤집기 직전의 클레임 종결 시각. 개입이 claims.resolved_at을 자기 시각으로 덮으므로
     * 이 칼럼이 없으면 셀러가 언제 거부했는지가 사라진다(종결 시각 칼럼이 한 칸뿐이다).
     */
    @Column(updatable = false)
    val previousResolvedAt: LocalDateTime? = previousResolvedAt

    /** 운영 내부 공유용 메모. 셀러·구매자에게 내려보내지 않는다. */
    @Column(updatable = false, length = 300)
    val internalMemo: String? = internalMemo?.trim()?.takeIf { it.isNotEmpty() }?.take(INTERNAL_MEMO_MAX_LENGTH)

    companion object {
        const val REASON_DETAIL_MAX_LENGTH = 500

        const val INTERNAL_MEMO_MAX_LENGTH = 300

        /**
         * 기록 생성의 유일한 진입점은 [Claim.overrideByAdmin]이다 — 뒤집기와 기록이 한 호출로 묶여야
         * "개입했는데 기록이 없다"가 구조적으로 불가능해진다. 그래서 뒤집은 결과와 직전 상태를 함께 받는다.
         */
        internal fun record(
            claim: Claim,
            adminId: Long,
            faultDecision: ClaimFaultDecision,
            reason: ClaimInterventionReason,
            reasonDetail: String?,
            previousStatus: ClaimStatus,
            previousSellerApprovalStatus: SellerApprovalStatus?,
            previousResolvedAt: LocalDateTime?,
            internalMemo: String?,
        ): ClaimIntervention {
            require(claim.id > 0L) { "개입 기록은 저장된 클레임에만 걸 수 있습니다 — claim_id가 0으로 굳는다" }
            require(claim.status == ClaimStatus.ADMIN_OVERRIDDEN) {
                "개입 기록은 뒤집은 뒤에 뜹니다 — 클레임에 실제로 적용된 부담 주체를 그대로 담아야 한다"
            }
            require(previousStatus != ClaimStatus.ADMIN_OVERRIDDEN) {
                "직전 상태가 ADMIN_OVERRIDDEN이면 무엇을 덮었는지가 사라진다"
            }
            return ClaimIntervention(
                claimId = claim.id,
                adminId = adminId,
                faultDecision = faultDecision,
                reason = reason,
                reasonDetail = reasonDetail,
                // 요청값이 아니라 클레임에 실제로 적용된 값 — 취소 클레임에서 비워진 것까지 기록이 따라간다
                returnCostBearer = claim.returnCostBearer,
                previousStatus = previousStatus,
                previousSellerApprovalStatus = previousSellerApprovalStatus,
                previousResolvedAt = previousResolvedAt,
                internalMemo = internalMemo,
            )
        }
    }
}
