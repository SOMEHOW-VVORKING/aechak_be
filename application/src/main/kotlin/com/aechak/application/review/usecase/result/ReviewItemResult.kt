package com.aechak.application.review.usecase.result

import com.aechak.application.review.port.view.ReviewView
import com.aechak.domain.review.review.enums.ReviewStatus
import java.time.LocalDateTime

data class ReviewItemResult(
    val reviewId: Long,
    val rating: Int,
    val content: String,
    val optionName: String,
    val authorNickname: String,
    val authorProfileImageUrl: String?,
    val createdAt: LocalDateTime,
    val images: List<ReviewImageItemResult>,
    val isMine: Boolean,
) {
    companion object {
        fun from(
            view: ReviewView,
            images: List<ReviewImageItemResult>,
            authorNickname: String,
            authorProfileImageUrl: String?,
            isMine: Boolean,
        ): ReviewItemResult =
            ReviewItemResult(
                reviewId = view.id,
                rating = view.rating,
                content = resolveContent(view),
                optionName = view.optionNameSnapshot,
                authorNickname = authorNickname,
                authorProfileImageUrl = authorProfileImageUrl,
                createdAt = view.createdAt,
                images = images,
                isMine = isMine,
            )

        private fun resolveContent(view: ReviewView): String =
            when (view.reviewStatus) {
                ReviewStatus.MASKED -> view.displayContent ?: BLINDED_CONTENT
                else -> view.content
            }

        private const val BLINDED_CONTENT = "블라인드 처리된 리뷰입니다."
    }
}

data class ReviewImageItemResult(
    val imageUrl: String,
    val sortOrder: Int,
)
