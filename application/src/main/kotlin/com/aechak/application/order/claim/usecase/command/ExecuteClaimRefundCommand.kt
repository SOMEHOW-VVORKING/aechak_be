package com.aechak.application.order.claim.usecase.command

/** 환불 집행 대상 클레임. 금액은 요청 시점에 확정 저장된 값을 쓰므로 명세에 담지 않는다. */
data class ExecuteClaimRefundCommand(
    val claimPublicId: String,
)
