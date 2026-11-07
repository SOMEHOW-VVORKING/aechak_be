package com.aechak.application.auth.usecase.command

data class AdminPasswordLoginCommand(
    val loginId: String,
    val password: String,
)
