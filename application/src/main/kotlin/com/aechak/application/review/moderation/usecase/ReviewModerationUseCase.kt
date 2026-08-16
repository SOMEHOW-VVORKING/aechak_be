package com.aechak.application.review.moderation.usecase

/** 리뷰 모더레이션 진입점. 작성 이벤트를 받은 컨슈머가 리뷰 하나의 판정을 요청한다. */
interface ReviewModerationUseCase {
    fun moderate(reviewId: Long)
}
