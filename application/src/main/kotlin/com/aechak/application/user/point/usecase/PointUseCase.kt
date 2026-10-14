package com.aechak.application.user.point.usecase

import com.aechak.application.user.point.usecase.command.UsePointCommand
import com.aechak.application.user.point.usecase.result.PointBalanceResult

/**
 * 적립금 진입점 계약. 잔액과 원장의 변이는 user 도메인 소유라 타 도메인은 반드시 이 UseCase를 경유한다.
 * 복구(RELEASE)와 구매확정 적립은 필요해지는 도메인 PR에서 메서드를 추가한다. 내역 조회는 MVP 화면 없음 — 범위 밖.
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
     * 사용 차감 — 잔액 조건부 원자 차감 + USE 원장 1행. 잔액 부족이면 30101.
     * 호출 트랜잭션에 참여한다(REQUIRED) — 주문 생성처럼 자산 확보가 한 트랜잭션이어야 하는 흐름 전제.
     */
    fun usePoint(command: UsePointCommand)
}
