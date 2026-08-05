package com.aechak.application.user.point.usecase

import com.aechak.application.user.point.usecase.result.PointBalanceResult

/**
 * 적립금 진입점 계약 — 적립금 중 조회만 user 도메인 소관.
 * 적립(구매확정·리뷰)·사용·복구는 주문/리뷰 도메인이 원장에 쓴다. 내역 조회는 MVP 화면 없음 — 범위 밖.
 */
interface PointUseCase {
    /** 내 적립금 잔액(원, 0 이상 정수) — 마이·주문서 화면 표시용 단건. */
    fun getMyPointBalance(userId: Long): PointBalanceResult
}
