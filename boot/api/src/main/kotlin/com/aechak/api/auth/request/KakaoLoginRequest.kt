package com.aechak.api.auth.request

import com.aechak.application.auth.usecase.command.SocialLoginCommand
import com.aechak.domain.user.social.enums.SocialProvider
import jakarta.validation.constraints.NotBlank

/** 네이티브 SDK 전용 — SDK가 받은 id_token을 그대로 제출한다. 웹은 서버 콜백 채널(WebAuthController)을 쓴다. */
data class KakaoLoginRequest(
    @field:NotBlank val idToken: String,
) {
    fun toCommand() =
        SocialLoginCommand(
            provider = SocialProvider.KAKAO,
            idToken = idToken,
        )
}
