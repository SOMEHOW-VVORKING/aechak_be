package com.aechak.application.user.point.service

import com.aechak.application.user.user.service.UserService
import com.aechak.domain.user.point.PointTransaction
import com.aechak.domain.user.point.enums.PointTransactionType
import com.aechak.domain.user.point.policy.ReviewRewardPolicy
import com.aechak.domain.user.point.repository.PointTransactionRepository
import com.aechak.domain.user.user.repository.UserRepository
import org.springframework.stereotype.Service

@Service
class PointCommandService(
    private val pointTransactionRepository: PointTransactionRepository,
    private val userService: UserService,
    private val userRepository: UserRepository,
) {
    /** 리뷰 적립 지급 */
    fun earnReviewReward(
        buyerUserId: Long,
        reviewId: Long,
        hasPhoto: Boolean,
    ) {
        val idempotencyKey = reviewRewardKey(reviewId)
        if (pointTransactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            return
        }
        val amount = ReviewRewardPolicy.amountFor(hasPhoto)
        val buyer = userService.getById(buyerUserId)
        pointTransactionRepository.save(
            PointTransaction.record(
                buyer = buyer,
                amount = amount,
                transactionType = PointTransactionType.EARN,
                idempotencyKey = idempotencyKey,
                sourceType = SOURCE_TYPE,
                sourceId = reviewId,
            ),
        )
        userRepository.addPointBalance(buyerUserId, amount)
    }

    companion object {
        private const val SOURCE_TYPE = "REVIEW_REWARD"

        private fun reviewRewardKey(reviewId: Long): String = "EARN:$SOURCE_TYPE:$reviewId"
    }
}
