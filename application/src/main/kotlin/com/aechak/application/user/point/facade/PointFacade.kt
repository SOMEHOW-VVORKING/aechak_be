package com.aechak.application.user.point.facade

import com.aechak.application.user.point.service.PointCommandService
import com.aechak.application.user.point.usecase.PointUseCase
import com.aechak.application.user.point.usecase.command.UsePointCommand
import com.aechak.application.user.point.usecase.result.PointBalanceResult
import com.aechak.application.user.user.service.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 잔액 읽기는 users.point_balance 비정규화 캐시를 그대로 반환한다 —
 * SoT는 point_transactions 원장이고, 캐시와 원장의 변이는 usePoint처럼 한 트랜잭션에서 함께 수행한다.
 */
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

    @Transactional
    override fun usePoint(command: UsePointCommand) {
        pointCommandService.usePoint(command)
    }
}
