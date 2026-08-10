package com.aechak.application.user.verification.usecase.command

data class ConfirmPhoneCodeCommand(
    val userId: Long,
    val phoneNumber: String,
    val code: String,
)
