package com.aechak.application.review.seller.usecase.command

/** 셀러 답글 등록·수정 입력. 답글은 리뷰당 하나이므로 리뷰 식별자가 곧 답글의 주소다. */
data class WriteReviewReplyCommand(
    val sellerId: Long,
    val reviewId: Long,
    val content: String,
)
