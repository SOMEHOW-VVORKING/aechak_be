package com.aechak.application.user.point.usecase

import com.aechak.application.user.point.usecase.result.PointBalanceResult

/** 적립금 진입점. 잔액 조회와 리뷰 적립 지급 */
interface PointUseCase {
    /** 내 적립금 잔액(원, 0 이상 정수) — 마이·주문서 화면 표시용 단건. */
    fun getMyPointBalance(userId: Long): PointBalanceResult

    /** 리뷰 작성 적립 지급 */
    fun earnReviewReward(
        buyerUserId: Long,
        reviewId: Long,
        hasPhoto: Boolean,
    )
}
