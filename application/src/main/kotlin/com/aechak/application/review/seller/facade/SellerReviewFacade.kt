package com.aechak.application.review.seller.facade

import com.aechak.application.file.usecase.FileUseCase
import com.aechak.application.review.seller.service.ReviewReplyService
import com.aechak.application.review.seller.service.SellerReviewQueryService
import com.aechak.application.review.seller.usecase.SellerReviewUseCase
import com.aechak.application.review.seller.usecase.command.WriteReviewReplyCommand
import com.aechak.application.review.seller.usecase.query.SellerReviewListQuery
import com.aechak.application.review.seller.usecase.result.SellerReviewItemResult
import com.aechak.application.review.seller.usecase.result.SellerReviewListResult
import com.aechak.application.review.usecase.result.ReviewImageItemResult
import com.aechak.application.review.usecase.result.ReviewRatingSummaryResult
import com.aechak.application.review.usecase.result.ReviewReplyResult
import com.aechak.application.seller.usecase.SellerUseCase
import com.aechak.application.user.user.usecase.UserUseCase
import com.aechak.common.error.BusinessException
import com.aechak.domain.review.error.ReviewErrorCode
import com.aechak.domain.seller.seller.enums.SellerStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SellerReviewFacade(
    private val sellerReviewQueryService: SellerReviewQueryService,
    private val reviewReplyService: ReviewReplyService,
    private val sellerUseCase: SellerUseCase,
    private val userUseCase: UserUseCase,
    private val fileUseCase: FileUseCase,
) : SellerReviewUseCase {
    @Transactional(readOnly = true)
    override fun getMyProductReviews(query: SellerReviewListQuery): SellerReviewListResult {
        requireReviewReadableSeller(query.sellerId)

        // 분포 집계는 첫 페이지에서만 센다. 별점·답글 필터가 없으면 그 합이 총건수와 같으므로 COUNT를 따로 돌리지 않는다.
        val summary =
            if (query.cursor == null) {
                ReviewRatingSummaryResult.from(sellerReviewQueryService.getRatingBuckets(query))
            } else {
                null
            }
        val totalCount =
            when {
                query.cursor != null -> null
                query.rating == null && query.hasReply == null -> summary?.reviewCount
                else -> sellerReviewQueryService.countFiltered(query)
            }

        val page = sellerReviewQueryService.getPage(query, totalCount)
        val imagesByReviewId = imagesByReviewId(page.items.map { it.id })
        val authorsById = userUseCase.getAuthors(page.items.map { it.authorUserId })

        return SellerReviewListResult(
            summary = summary,
            page =
                page.map { view ->
                    SellerReviewItemResult.from(
                        view = view,
                        images = imagesByReviewId[view.id].orEmpty(),
                        authorNickname = authorsById.getValue(view.authorUserId).nickname,
                    )
                },
        )
    }

    @Transactional
    override fun writeReply(command: WriteReviewReplyCommand): ReviewReplyResult {
        requireReplyWritableSeller(command.sellerId)
        return ReviewReplyResult.from(reviewReplyService.create(command))
    }

    @Transactional
    override fun editReply(command: WriteReviewReplyCommand): ReviewReplyResult {
        requireReplyWritableSeller(command.sellerId)
        return ReviewReplyResult.from(reviewReplyService.edit(command))
    }

    @Transactional
    override fun deleteReply(
        sellerId: Long,
        reviewId: Long,
    ) {
        requireReplyWritableSeller(sellerId)
        reviewReplyService.delete(sellerId, reviewId)
    }

    private fun imagesByReviewId(reviewIds: List<Long>): Map<Long, List<ReviewImageItemResult>> =
        sellerReviewQueryService
            .getImagesByReviewIds(reviewIds)
            .groupBy { it.reviewId }
            .mapValues { (_, images) ->
                images.map { ReviewImageItemResult(fileUseCase.resolveMediaUrl(it.storageKey)!!, it.sortOrder) }
            }

    /** 쓰기 자격 게이트 — 답글은 공개 글이라 영업 자격이 살아 있어야 한다. 휴점(PAUSED)은 응대의 일부이므로 허용한다. */
    private fun requireReplyWritableSeller(sellerId: Long) {
        if (sellerUseCase.requireSellerStatus(sellerId) !in REPLY_WRITABLE_SELLER_STATUSES) {
            throw BusinessException(ReviewErrorCode.REVIEW_SELLER_ACCESS_DENIED)
        }
    }

    /** 조회 자격 게이트 — 자격 소멸(탈퇴)과 제재(정지)만 막는다. 휴점·탈퇴신청 셀러는 자기 리뷰를 본다 */
    private fun requireReviewReadableSeller(sellerId: Long) {
        if (sellerUseCase.requireSellerStatus(sellerId) !in REVIEW_READABLE_SELLER_STATUSES) {
            throw BusinessException(ReviewErrorCode.REVIEW_SELLER_ACCESS_DENIED)
        }
    }

    private companion object {
        val REVIEW_READABLE_SELLER_STATUSES =
            setOf(SellerStatus.ACTIVE, SellerStatus.PAUSED, SellerStatus.WITHDRAWAL_REQUESTED)

        val REPLY_WRITABLE_SELLER_STATUSES = setOf(SellerStatus.ACTIVE, SellerStatus.PAUSED)
    }
}
