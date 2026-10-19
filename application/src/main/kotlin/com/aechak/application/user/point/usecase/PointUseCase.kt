package com.aechak.application.user.point.usecase

import com.aechak.application.user.point.usecase.command.EarnPurchaseRewardCommand
import com.aechak.application.user.point.usecase.command.UsePointCommand
import com.aechak.application.user.point.usecase.result.PointBalanceResult

/**
 * 적립금 진입점 계약. 잔액과 원장의 변이는 user 도메인 소유라 타 도메인은 반드시 이 UseCase를 경유한다.
 * 복구(RELEASE)는 필요해지는 도메인 PR에서 메서드를 추가한다. 내역 조회는 MVP 화면 없음 — 범위 밖.
 */
interface PointUseCase {
    fun getMyPointBalance(userId: Long): PointBalanceResult

    /** 리뷰 작성 적립 지급 */
    fun earnReviewReward(
        buyerUserId: Long,
        reviewId: Long,
        hasPhoto: Boolean,
    )

    /**
     * 구매확정 적립 지급 — 적립률 적용과 원장 1행. 같은 주문의 재호출은 멱등키로 접혀 두 번 지급되지 않는다.
     * 호출 트랜잭션에 참여한다(REQUIRED) — 확정과 적립이 갈라지면 확정됐는데 적립이 없는 주문이 남는다.
     */
    fun earnPurchaseReward(command: EarnPurchaseRewardCommand)

    /**
     * 사용 차감 — 잔액 조건부 원자 차감 + USE 원장 1행. 잔액 부족이면 30101.
     * 호출 트랜잭션에 참여한다(REQUIRED) — 주문 생성처럼 자산 확보가 한 트랜잭션이어야 하는 흐름 전제.
     */
    fun usePoint(command: UsePointCommand)
}
