package com.aechak.application.user.verification.usecase.command

data class SendPhoneCodeCommand(
    val userId: Long,
    val phoneNumber: String,
)
