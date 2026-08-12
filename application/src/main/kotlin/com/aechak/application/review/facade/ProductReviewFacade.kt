package com.aechak.application.review.facade

import com.aechak.application.file.usecase.FileUseCase
import com.aechak.application.product.usecase.ProductUseCase
import com.aechak.application.review.service.ProductReviewService
import com.aechak.application.review.usecase.ProductReviewUseCase
import com.aechak.application.review.usecase.query.ProductReviewListQuery
import com.aechak.application.review.usecase.result.ProductReviewListResult
import com.aechak.application.review.usecase.result.ReviewImageItemResult
import com.aechak.application.review.usecase.result.ReviewItemResult
import com.aechak.application.review.usecase.result.ReviewRatingSummaryResult
import com.aechak.application.user.user.usecase.UserUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProductReviewFacade(
    private val productReviewService: ProductReviewService,
    private val productUseCase: ProductUseCase,
    private val userUseCase: UserUseCase,
    private val fileUseCase: FileUseCase,
) : ProductReviewUseCase {
    @Transactional(readOnly = true)
    override fun getProductReviews(query: ProductReviewListQuery): ProductReviewListResult {
        val productId = productUseCase.getVisibleProductId(query.productPublicId)
        // 요약은 페이지마다 같으니 첫 페이지에서만 계산한다(totalCount와 동일).
        val summary =
            if (query.cursor == null) {
                ReviewRatingSummaryResult.from(productReviewService.getRatingBuckets(productId))
            } else {
                null
            }

        val page = productReviewService.getVisiblePage(productId, query)
        val imagesByReviewId =
            productReviewService
                .getImagesByReviewIds(page.items.map { it.id })
                .groupBy { it.reviewId }
        val authorsById = userUseCase.getAuthors(page.items.map { it.authorUserId })

        val itemPage =
            page.map { view ->
                val author = authorsById.getValue(view.authorUserId)
                val images =
                    imagesByReviewId[view.id]
                        .orEmpty()
                        .map { ReviewImageItemResult(fileUseCase.resolveMediaUrl(it.storageKey)!!, it.sortOrder) }
                ReviewItemResult.from(view, images, author.nickname, author.profileImageUrl)
            }

        return ProductReviewListResult(summary = summary, page = itemPage)
    }
}
