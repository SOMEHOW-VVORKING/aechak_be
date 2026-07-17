package com.aechak.api.auth.cookie

import com.aechak.application.auth.service.TokenPolicy
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * refresh 쿠키 정책의 단일 소유자 — 발급과 파기가 같은 속성(httpOnly·Secure·SameSite·Path)을
 * 공유해야 브라우저가 동일 쿠키로 인식하므로, 속성 결정을 한 곳에 모은다.
 *
 * - Path를 auth 계열로 한정 — 이 쿠키는 다른 API 호출에는 아예 실리지 않는다(노출면 축소).
 * - Secure는 요청 스킴 기반 자동 — local(http)은 off, prod(https)는 on.
 *   prod에서 TLS가 LB에서 종료되면 forward-headers 설정이 전제(배포 체크리스트).
 */
@Component
class RefreshCookieFactory(
    private val tokenPolicy: TokenPolicy,
    @param:Value("\${api.base-path}") private val basePath: String,
) {
    fun issue(
        refreshToken: String,
        request: HttpServletRequest,
    ): ResponseCookie = builder(refreshToken, request).maxAge(tokenPolicy.refreshTtl).build()

    /** 파기 = 빈 값 + maxAge 0 — 발급과 속성이 같아야 브라우저가 기존 쿠키를 덮어쓴다. */
    fun expire(request: HttpServletRequest): ResponseCookie = builder("", request).maxAge(Duration.ZERO).build()

    private fun builder(
        value: String,
        request: HttpServletRequest,
    ): ResponseCookie.ResponseCookieBuilder =
        ResponseCookie
            .from(REFRESH_COOKIE, value)
            .httpOnly(true)
            .secure(request.isSecure)
            .sameSite("Lax")
            .path("$basePath/auth")

    companion object {
        const val REFRESH_COOKIE = "refresh_token"
    }
}
