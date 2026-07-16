package com.aechak.api.auth.request

import com.aechak.application.auth.usecase.command.SocialLoginCommand
import com.aechak.domain.user.social.enums.SocialProvider
import jakarta.validation.constraints.AssertTrue

/**
 * 두 채널 중 정확히 하나로 요청한다:
 * - 네이티브 SDK: idToken
 * - 웹(JS SDK): code + redirectUri (서버가 id_token으로 교환)
 * 교차 필드 검증 위반은 @Valid 경로를 타고 400(90002)으로 떨어진다.
 */
data class KakaoLoginRequest(
    val idToken: String? = null,
    val code: String? = null,
    val redirectUri: String? = null,
) {
    @get:AssertTrue(message = "idToken 또는 code 중 정확히 하나를 제출해야 합니다")
    val loginMethodValid: Boolean
        get() = (idToken != null) xor (code != null)

    @get:AssertTrue(message = "code 채널은 redirectUri가 필요합니다")
    val redirectUriPresentForCode: Boolean
        get() = code == null || redirectUri != null

    fun toCommand() =
        SocialLoginCommand(
            provider = SocialProvider.KAKAO,
            idToken = idToken,
            code = code,
            redirectUri = redirectUri,
        )
}
