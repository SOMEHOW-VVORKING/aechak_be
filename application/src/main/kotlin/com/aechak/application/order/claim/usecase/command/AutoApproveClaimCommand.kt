package com.aechak.application.order.claim.usecase.command

/** 자동승인 대상 클레임. 기한은 요청 시점에 확정 저장된 값을 쓰므로 명세에 담지 않는다. */
data class AutoApproveClaimCommand(
    val claimPublicId: String,
)
