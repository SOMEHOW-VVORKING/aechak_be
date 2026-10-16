package com.aechak.application.settlement.usecase.command

/** accountNumberEnc는 신청서의 AES 암호문(Base64) 그대로 — 평문 왕복 없이 이관한다. */
data class RegisterVerifiedAccountCommand(
    val sellerId: Long,
    val bankCode: String,
    val accountNumberEnc: String,
    val accountHolderName: String,
)
