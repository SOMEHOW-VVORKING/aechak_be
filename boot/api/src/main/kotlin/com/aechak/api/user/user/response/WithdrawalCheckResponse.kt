package com.aechak.api.user.user.response

import com.aechak.application.user.withdrawal.usecase.result.WithdrawalBlockReason
import com.aechak.application.user.withdrawal.usecase.result.WithdrawalCheckResult

data class WithdrawalCheckResponse(
    val withdrawable: Boolean,
    val blockers: List<Blocker>,
) {
    data class Blocker(
        val reason: WithdrawalBlockReason,
        val count: Int,
    )

    companion object {
        fun from(result: WithdrawalCheckResult): WithdrawalCheckResponse =
            WithdrawalCheckResponse(
                withdrawable = result.withdrawable,
                blockers = result.blockers.map { Blocker(it.reason, it.count) },
            )
    }
}
