package com.aechak.api.review.response

import com.aechak.application.review.usecase.result.MyReviewListItemResult
import com.aechak.application.review.usecase.result.UnreviewedOrderItemResult
import com.aechak.application.review.usecase.result.WrittenReviewItemResult
import com.aechak.application.support.CursorPageResult
import com.aechak.domain.review.review.enums.ReviewStatus
import java.time.LocalDate
import java.time.LocalDateTime

data class MyReviewListResponse(
    val items: List<MyReviewListItemResponse>,
    val totalCount: Long?,
    val nextCursor: String?,
    val hasNext: Boolean,
) {
    companion object {
        fun from(page: CursorPageResult<MyReviewListItemResult>): MyReviewListResponse =
            MyReviewListResponse(
                items = page.items.map(::toItemResponse),
                totalCount = page.totalCount,
                nextCursor = page.nextCursor,
                hasNext = page.hasNext,
            )

        private fun toItemResponse(result: MyReviewListItemResult): MyReviewListItemResponse =
            when (result) {
                is WrittenReviewItemResult -> WrittenReviewItemResponse.from(result)
                is UnreviewedOrderItemResult -> UnreviewedOrderItemResponse.from(result)
            }
    }
}

/** 두 탭의 항목이 한 배열에 담기므로 type으로 어느 쪽인지 알린다 */
sealed interface MyReviewListItemResponse {
    val type: MyReviewItemType
}

enum class MyReviewItemType {
    WRITTEN,
    UNREVIEWED,
}

data class WrittenReviewItemResponse(
    val reviewId: Long,
    val rating: Int,
    val content: String,
    val reviewStatus: ReviewStatus,
    val optionName: String,
    val productPublicId: String,
    val productName: String,
    val productThumbnailUrl: String?,
    val createdAt: LocalDateTime,
    val images: List<ReviewImageResponse>,
    override val type: MyReviewItemType = MyReviewItemType.WRITTEN,
) : MyReviewListItemResponse {
    companion object {
        fun from(result: WrittenReviewItemResult): WrittenReviewItemResponse =
            WrittenReviewItemResponse(
                reviewId = result.reviewId,
                rating = result.rating,
                content = result.content,
                reviewStatus = result.reviewStatus,
                optionName = result.optionName,
                productPublicId = result.productPublicId,
                productName = result.productName,
                productThumbnailUrl = result.productThumbnailUrl,
                createdAt = result.createdAt,
                images = result.images.map(ReviewImageResponse::from),
            )
    }
}

data class UnreviewedOrderItemResponse(
    val orderItemId: Long,
    val optionName: String,
    val productPublicId: String,
    val productName: String,
    val productThumbnailUrl: String?,
    val purchaseConfirmedAt: LocalDateTime,
    val reviewableUntil: LocalDate,
    val canReview: Boolean,
    override val type: MyReviewItemType = MyReviewItemType.UNREVIEWED,
) : MyReviewListItemResponse {
    companion object {
        fun from(result: UnreviewedOrderItemResult): UnreviewedOrderItemResponse =
            UnreviewedOrderItemResponse(
                orderItemId = result.orderItemId,
                optionName = result.optionName,
                productPublicId = result.productPublicId,
                productName = result.productName,
                productThumbnailUrl = result.productThumbnailUrl,
                purchaseConfirmedAt = result.purchaseConfirmedAt,
                reviewableUntil = result.reviewableUntil,
                canReview = result.canReview,
            )
    }
}
