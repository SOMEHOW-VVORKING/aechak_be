package com.aechak.application.user.point.usecase

import com.aechak.application.user.point.usecase.command.UsePointCommand
import com.aechak.application.user.point.usecase.result.PointBalanceResult

/**
 * 적립금 진입점 계약. 잔액과 원장의 변이는 user 도메인 소유라 타 도메인은 반드시 이 UseCase를 경유한다.
 * 적립(구매확정·리뷰)·복구(RELEASE)는 필요해지는 도메인 PR에서 메서드를 추가한다. 내역 조회는 MVP 화면 없음 — 범위 밖.
 */
interface PointUseCase {
    /** 내 적립금 잔액(원, 0 이상 정수) — 마이·주문서 화면 표시용 단건. */
    fun getMyPointBalance(userId: Long): PointBalanceResult

    /**
     * 사용 차감 — 잔액 조건부 원자 차감 + USE 원장 1행. 잔액 부족이면 30101.
     * 호출 트랜잭션에 참여한다(REQUIRED) — 주문 생성처럼 자산 확보가 한 트랜잭션이어야 하는 흐름 전제.
     */
    fun usePoint(command: UsePointCommand)
}
