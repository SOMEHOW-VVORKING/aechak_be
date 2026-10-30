package com.aechak.application.review.seller.port.view

import com.aechak.domain.review.review.enums.ReviewStatus
import java.time.LocalDateTime

/** 셀러 목록 한 행 — 답글은 같은 쿼리의 left join으로 함께 읽어 행마다 추가 조회가 생기지 않게 한다. */
data class SellerReviewView(
    val id: Long,
    val productPublicId: String,
    val productName: String,
    val rating: Int,
    val content: String,
    val displayContent: String?,
    val reviewStatus: ReviewStatus,
    val optionNameSnapshot: String,
    val authorUserId: Long,
    val createdAt: LocalDateTime,
    val replyId: Long?,
    val replyContent: String?,
    val replyRepliedAt: LocalDateTime?,
    val replyUpdatedAt: LocalDateTime?,
)
