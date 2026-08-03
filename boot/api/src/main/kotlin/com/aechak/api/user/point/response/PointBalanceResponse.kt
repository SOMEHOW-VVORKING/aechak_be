package com.aechak.api.user.point.response

import com.aechak.application.user.point.usecase.result.PointBalanceResult

data class PointBalanceResponse(
    val balance: Long,
) {
    companion object {
        fun from(result: PointBalanceResult): PointBalanceResponse = PointBalanceResponse(balance = result.balance)
    }
}
