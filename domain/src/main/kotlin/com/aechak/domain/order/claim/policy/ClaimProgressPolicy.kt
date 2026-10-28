package com.aechak.domain.order.claim.policy

import com.aechak.domain.order.claim.enums.ClaimStatus

/** 결론이 나지 않은 클레임 상태. 구매확정(수동·자동)이 이 상태의 클레임을 앞지르지 못하게 막는 기준이다. */
object ClaimProgressPolicy {
    val IN_PROGRESS: Set<ClaimStatus> =
        setOf(
            ClaimStatus.CLAIM_REQUESTED,
            ClaimStatus.COLLECTING,
            // 환불이 실패로 멈춘 건은 돌려줄 돈이 남아 있어 아직 끝난 클레임이 아니다
            ClaimStatus.REFUND_FAILED,
        )
}
