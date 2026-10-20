package com.aechak.application.order.claim.usecase.command

/** 운영 메모 덮어쓰기. null·공백은 삭제로 본다. */
data class UpdateClaimMemoCommand(
    val sellerId: Long,
    val claimPublicId: String,
    val sellerMemo: String?,
)
