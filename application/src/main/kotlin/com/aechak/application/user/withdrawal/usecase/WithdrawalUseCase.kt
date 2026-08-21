package com.aechak.application.user.withdrawal.usecase

import com.aechak.application.user.withdrawal.usecase.result.WithdrawalCheckResult

interface WithdrawalUseCase {
    fun checkWithdrawal(userId: Long): WithdrawalCheckResult

    fun withdraw(userId: Long)
}
