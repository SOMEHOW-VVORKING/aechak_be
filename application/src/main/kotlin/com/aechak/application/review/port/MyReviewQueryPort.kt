package com.aechak.application.review.port

import com.aechak.application.review.port.view.UnreviewedOrderItemView
import com.aechak.application.review.port.view.WrittenReviewView
import java.time.LocalDateTime

interface MyReviewQueryPort {
    fun findWrittenReviewPage(condition: WrittenReviewListCondition): List<WrittenReviewView>

    fun countWrittenReviews(authorUserId: Long): Long

    fun findUnreviewedOrderItemPage(condition: UnreviewedOrderItemListCondition): List<UnreviewedOrderItemView>

    fun countUnreviewedOrderItems(buyerId: Long): Long
}

data class WrittenReviewAnchor(
    val lastReviewId: Long,
)

// 정렬 키가 둘이라 앵커도 한 덩어리로 둔다. 나눠 두면 한쪽만 채워진 상태가 만들어진다
data class UnreviewedOrderItemAnchor(
    val lastConfirmedAt: LocalDateTime,
    val lastOrderItemId: Long,
)

data class WrittenReviewListCondition(
    val authorUserId: Long,
    val anchor: WrittenReviewAnchor?,
    val limit: Int,
) {
    init {
        require(limit > 0) { "limit은 양수여야 합니다." }
    }
}

data class UnreviewedOrderItemListCondition(
    val buyerId: Long,
    val anchor: UnreviewedOrderItemAnchor?,
    val limit: Int,
) {
    init {
        require(limit > 0) { "limit은 양수여야 합니다." }
    }
}
