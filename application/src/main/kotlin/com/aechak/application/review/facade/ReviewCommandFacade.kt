package com.aechak.application.review.facade

import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.application.file.usecase.FileUseCase
import com.aechak.application.file.usecase.command.PromoteFileCommand
import com.aechak.application.messaging.MessagePublisher
import com.aechak.application.order.usecase.OrderUseCase
import com.aechak.application.review.service.ReviewCommandService
import com.aechak.application.review.usecase.ReviewCommandUseCase
import com.aechak.application.review.usecase.command.CreateReviewCommand
import com.aechak.application.review.usecase.result.CreateReviewResult
import com.aechak.application.review.usecase.result.ReviewImageItemResult
import com.aechak.common.error.BusinessException
import com.aechak.domain.review.error.ReviewErrorCode
import com.aechak.domain.review.review.Review
import com.aechak.domain.review.review.ReviewImage
import com.aechak.message.review.ReviewCreatedMessage
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

@Service
class ReviewCommandFacade(
    private val reviewCommandService: ReviewCommandService,
    private val orderUseCase: OrderUseCase,
    private val fileUseCase: FileUseCase,
    private val messagePublisher: MessagePublisher,
    private val transactionTemplate: TransactionTemplate,
) : ReviewCommandUseCase {
    override fun createReview(command: CreateReviewCommand): CreateReviewResult {
        val orderItem =
            orderUseCase.getOrderItemForReview(command.orderItemId, command.userId)
                ?: throw BusinessException(ReviewErrorCode.REVIEW_ORDER_ITEM_NOT_FOUND)
        reviewCommandService.ensureCanCreateReview(command, orderItem)

        val imageKeys = command.imageKeys.distinct()
        if (imageKeys.size > Review.MAX_IMAGES) {
            throw BusinessException(ReviewErrorCode.REVIEW_TOO_MANY_IMAGES)
        }

        val images =
            imageKeys.mapIndexed { index, tmpKey ->
                val key = fileUseCase.promote(PromoteFileCommand(tmpKey, command.userId, UploadPurpose.REVIEW_IMAGE)).key
                ReviewImage.of(key, index)
            }

        return transactionTemplate.execute {
            val review = reviewCommandService.create(command, orderItem, images)
            messagePublisher.publish(
                ReviewCreatedMessage(
                    reviewId = review.id,
                    productId = review.productId,
                    buyerUserId = review.authorUserId,
                    hasPhoto = review.images.isNotEmpty(),
                ),
            )
            CreateReviewResult.from(
                review,
                review.images.map { ReviewImageItemResult(fileUseCase.resolveMediaUrl(it.storageKey)!!, it.sortOrder) },
            )
        }
    }

    @Transactional
    override fun deleteReview(
        userId: Long,
        reviewId: Long,
    ) {
        reviewCommandService.delete(userId, reviewId)
    }
}
