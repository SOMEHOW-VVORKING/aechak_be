package com.aechak.application.user.point.facade

import com.aechak.application.user.point.service.PointCommandService
import com.aechak.application.user.point.usecase.PointUseCase
import com.aechak.application.user.point.usecase.result.PointBalanceResult
import com.aechak.application.user.user.service.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PointFacade(
    private val userService: UserService,
    private val pointCommandService: PointCommandService,
) : PointUseCase {
    @Transactional(readOnly = true)
    override fun getMyPointBalance(userId: Long): PointBalanceResult =
        PointBalanceResult(balance = userService.getById(userId).pointBalance)

    @Transactional
    override fun earnReviewReward(
        buyerUserId: Long,
        reviewId: Long,
        hasPhoto: Boolean,
    ) {
        pointCommandService.earnReviewReward(buyerUserId, reviewId, hasPhoto)
    }
}
