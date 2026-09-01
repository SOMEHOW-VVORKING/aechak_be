package com.aechak.application.user.point.service

import com.aechak.application.user.point.usecase.command.ReleasePointCommand
import com.aechak.common.error.BusinessException
import com.aechak.domain.user.error.UserErrorCode
import com.aechak.domain.user.point.PointTransaction
import com.aechak.domain.user.point.enums.PointTransactionType
import com.aechak.domain.user.point.repository.PointTransactionRepository
import com.aechak.domain.user.user.User
import com.aechak.domain.user.user.repository.UserRepository
import org.springframework.stereotype.Service

@Service
class PointService(
    private val userRepository: UserRepository,
    private val pointTransactionRepository: PointTransactionRepository,
) {
    fun releasePoint(
        command: ReleasePointCommand,
        buyer: User,
    ) {
        if (command.amount <= 0) {
            throw BusinessException(UserErrorCode.INVALID_POINT_AMOUNT)
        }
        if (!userRepository.addPointBalance(command.userId, command.amount)) {
            throw BusinessException(UserErrorCode.USER_NOT_FOUND)
        }
        pointTransactionRepository.save(
            PointTransaction.record(
                buyer = buyer,
                amount = command.amount,
                transactionType = PointTransactionType.RELEASE,
                idempotencyKey = command.idempotencyKey,
                sourceType = command.sourceType,
                sourceId = command.sourceId,
            ),
        )
    }
}
