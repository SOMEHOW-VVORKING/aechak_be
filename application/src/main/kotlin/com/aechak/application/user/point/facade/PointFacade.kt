package com.aechak.application.user.point.facade

import com.aechak.application.user.point.usecase.PointUseCase
import com.aechak.application.user.point.usecase.result.PointBalanceResult
import com.aechak.application.user.user.service.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 잔액 읽기는 users.point_balance 비정규화 캐시를 그대로 반환한다 —
 * SoT(point_transactions 원장)와의 정합·캐시 갱신은 쓰기 도메인(주문/리뷰) 몫.
 */
@Service
class PointFacade(
    private val userService: UserService,
) : PointUseCase {
    @Transactional(readOnly = true)
    override fun getMyPointBalance(userId: Long): PointBalanceResult =
        PointBalanceResult(balance = userService.getById(userId).pointBalance)
}
