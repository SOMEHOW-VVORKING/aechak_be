package com.aechak.application.user.point.facade

import com.aechak.application.user.point.usecase.PointUseCase
import com.aechak.application.user.point.usecase.command.UsePointCommand
import com.aechak.application.user.point.usecase.result.PointBalanceResult
import com.aechak.application.user.user.service.UserService
import com.aechak.common.error.BusinessException
import com.aechak.domain.user.error.UserErrorCode
import com.aechak.domain.user.point.PointTransaction
import com.aechak.domain.user.point.enums.PointTransactionType
import com.aechak.domain.user.point.repository.PointTransactionRepository
import com.aechak.domain.user.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 잔액 읽기는 users.point_balance 비정규화 캐시를 그대로 반환한다 —
 * SoT는 point_transactions 원장이고, 캐시와 원장의 변이는 usePoint처럼 이 파사드가 한 트랜잭션에서 함께 수행한다.
 */
@Service
class PointFacade(
    private val userService: UserService,
    private val userRepository: UserRepository,
    private val pointTransactionRepository: PointTransactionRepository,
) : PointUseCase {
    @Transactional(readOnly = true)
    override fun getMyPointBalance(userId: Long): PointBalanceResult =
        PointBalanceResult(balance = userService.getById(userId).pointBalance)

    /** 최종 잔액 판정은 조건부 원자 UPDATE. 원장 멱등키 UNIQUE가 이중 기록의 최후 방어선이다 */
    @Transactional
    override fun usePoint(command: UsePointCommand) {
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
}
