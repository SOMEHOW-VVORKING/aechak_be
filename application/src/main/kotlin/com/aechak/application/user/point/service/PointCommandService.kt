package com.aechak.application.user.point.service

import com.aechak.application.user.point.usecase.command.UsePointCommand
import com.aechak.application.user.user.service.UserService
import com.aechak.common.error.BusinessException
import com.aechak.domain.user.error.UserErrorCode
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

    /**
     * 사용 차감 — 최종 잔액 판정은 조건부 원자 UPDATE.
     * 원장 멱등키 UNIQUE가 이중 기록의 최후 방어선이다.
     */
    fun usePoint(command: UsePointCommand) {
        if (command.amount <= 0) {
            // 음수가 UPDATE에 닿으면 잔액이 늘어난다 — 원장 검증과 별개로 차감 전에 막는다
            throw BusinessException(UserErrorCode.INVALID_POINT_AMOUNT)
        }
        if (!userRepository.deductPointBalance(command.userId, command.amount)) {
            throw BusinessException(UserErrorCode.INSUFFICIENT_POINT_BALANCE)
        }
        pointTransactionRepository.save(
            PointTransaction.record(
                buyer = userService.getById(command.userId),
                amount = command.amount,
                transactionType = PointTransactionType.USE,
                idempotencyKey = command.idempotencyKey,
                sourceType = command.sourceType,
                sourceId = command.sourceId,
            ),
        )
    }

    companion object {
        private const val SOURCE_TYPE = "REVIEW_REWARD"

        private fun reviewRewardKey(reviewId: Long): String = "EARN:$SOURCE_TYPE:$reviewId"
    }
}
