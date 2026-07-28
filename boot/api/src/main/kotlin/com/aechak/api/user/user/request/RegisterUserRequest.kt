package com.aechak.api.user.user.request

import com.aechak.application.user.user.usecase.command.RegisterUserCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * HTTP 요청 dto. 형식 검증 어노테이션(@NotBlank 등)은 전부 여기서 끝낸다 — Command에는 없다.
 * toCommand()를 같은 파일에 둬 API 스펙과 매핑이 한 화면에 보이게 한다.
 */
data class RegisterUserRequest(
    @field:NotBlank
    @field:Size(min = 2, max = 12)
    val nickname: String,
) {
    fun toCommand(): RegisterUserCommand = RegisterUserCommand(nickname = nickname)
}
