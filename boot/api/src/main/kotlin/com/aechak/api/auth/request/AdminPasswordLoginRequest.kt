package com.aechak.api.auth.request

import com.aechak.application.auth.usecase.command.AdminPasswordLoginCommand
import jakarta.validation.constraints.NotBlank

data class AdminPasswordLoginRequest(
    @field:NotBlank
    val loginId: String,
    @field:NotBlank
    val password: String,
) {
    fun toCommand() = AdminPasswordLoginCommand(loginId = loginId, password = password)
}
