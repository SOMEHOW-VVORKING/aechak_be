package com.aechak.api.auth.request

import com.aechak.application.auth.usecase.command.SocialLoginCommand
import com.aechak.domain.user.social.enums.SocialProvider
import jakarta.validation.constraints.NotBlank

/**
 * 네이티브·웹(SIWA JS) 공용 — 웹은 form_post 응답의 id_token·code를 같은 필드로 제출한다.
 * (카카오와 달리 애플 웹은 서버 교환이 불필요 — aud만 Services ID로 달라짐)
 */
data class AppleLoginRequest(
    @field:NotBlank val idToken: String,
    /** revoke용 refresh token 교환에 사용 — 1회용·약 5분 유효, 로그인마다 전달. */
    @field:NotBlank val authorizationCode: String,
    /** 애플이 최초 인가 1회만 제공 — 첫 로그인 때만 포함. */
    val fullName: FullName? = null,
) {
    /** 애플이 최초 인가 1회만 제공하는 이름 컴포넌트. */
    data class FullName(
        val familyName: String? = null,
        val givenName: String? = null,
    ) {
        fun formatted(): String? =
            listOfNotNull(familyName, givenName).joinToString(" ").ifBlank { null }
    }

    fun toCommand() = SocialLoginCommand(
        provider = SocialProvider.APPLE,
        idToken = idToken,
        authorizationCode = authorizationCode,
        appleFullName = fullName?.formatted(),
    )
}
