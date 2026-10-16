package com.aechak.api.user.user.response

import com.aechak.application.user.withdrawal.usecase.result.WithdrawalCheckResult

data class WithdrawalCheckResponse(
    val withdrawable: Boolean,
) {
    companion object {
        fun from(result: WithdrawalCheckResult): WithdrawalCheckResponse = WithdrawalCheckResponse(result.withdrawable)
    }
}
