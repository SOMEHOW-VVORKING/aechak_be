package com.aechak.application.review.facade

import com.aechak.application.file.usecase.FileUseCase
import com.aechak.application.product.product.usecase.ProductUseCase
import com.aechak.application.review.service.ReviewQueryService
import com.aechak.application.review.usecase.ReviewQueryUseCase
import com.aechak.application.review.usecase.query.MyReviewListQuery
import com.aechak.application.review.usecase.query.MyReviewTab
import com.aechak.application.review.usecase.query.ProductReviewListQuery
import com.aechak.application.review.usecase.result.MyReviewListItemResult
import com.aechak.application.review.usecase.result.ProductReviewListResult
import com.aechak.application.review.usecase.result.ReviewImageItemResult
import com.aechak.application.review.usecase.result.ReviewItemResult
import com.aechak.application.review.usecase.result.ReviewRatingSummaryResult
import com.aechak.application.review.usecase.result.UnreviewedOrderItemResult
import com.aechak.application.review.usecase.result.WrittenReviewItemResult
import com.aechak.application.support.CursorPageResult
import com.aechak.application.user.user.usecase.UserUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class ReviewQueryFacade(
    private val reviewQueryService: ReviewQueryService,
    private val productUseCase: ProductUseCase,
    private val userUseCase: UserUseCase,
    private val fileUseCase: FileUseCase,
) : ReviewQueryUseCase {
    @Transactional(readOnly = true)
    override fun getProductReviews(
        query: ProductReviewListQuery,
        viewerId: Long,
    ): ProductReviewListResult {
        val productId = productUseCase.getVisibleProductId(query.productPublicId)
        // 전체 평점 요약은 첫 페이지에만 포함
        val summary =
            if (query.cursor == null) {
                ReviewRatingSummaryResult.from(reviewQueryService.getRatingBuckets(productId))
            } else {
                null
            }

        val page = reviewQueryService.getVisiblePage(productId, query)
        val imagesByReviewId = imagesByReviewId(page.items.map { it.id })
        val authorsById = userUseCase.getAuthors(page.items.map { it.authorUserId })

        val itemPage =
            page.map { view ->
                val author = authorsById.getValue(view.authorUserId)
                ReviewItemResult.from(
                    view = view,
                    images = imagesByReviewId[view.id].orEmpty(),
                    authorNickname = author.nickname,
                    authorProfileImageUrl = author.profileImageUrl,
                    isMine = view.authorUserId == viewerId,
                )
            }

        return ProductReviewListResult(summary = summary, page = itemPage)
    }

    @Transactional(readOnly = true)
    override fun getMyReviews(query: MyReviewListQuery): CursorPageResult<MyReviewListItemResult> =
        when (query.tab) {
            MyReviewTab.WRITTEN -> writtenReviewPage(query)
            MyReviewTab.UNREVIEWED -> unreviewedOrderItemPage(query)
        }

    private fun writtenReviewPage(query: MyReviewListQuery): CursorPageResult<MyReviewListItemResult> {
        val page = reviewQueryService.getWrittenReviewPage(query)
        val imagesByReviewId = imagesByReviewId(page.items.map { it.reviewId })

        return page.map { view ->
            WrittenReviewItemResult.from(
                view = view,
                productThumbnailUrl = fileUseCase.resolveMediaUrl(view.thumbnailKeySnapshot),
                images = imagesByReviewId[view.reviewId].orEmpty(),
            )
        }
    }

    private fun unreviewedOrderItemPage(query: MyReviewListQuery): CursorPageResult<MyReviewListItemResult> {
        val page = reviewQueryService.getUnreviewedOrderItemPage(query)
        val now = LocalDateTime.now()

        return page.map { view ->
            UnreviewedOrderItemResult.from(view, fileUseCase.resolveMediaUrl(view.thumbnailKeySnapshot), now)
        }
    }

    private fun imagesByReviewId(reviewIds: List<Long>): Map<Long, List<ReviewImageItemResult>> =
        reviewQueryService
            .getImagesByReviewIds(reviewIds)
            .groupBy { it.reviewId }
            .mapValues { (_, images) ->
                images.map { ReviewImageItemResult(fileUseCase.resolveMediaUrl(it.storageKey)!!, it.sortOrder) }
            }
}
