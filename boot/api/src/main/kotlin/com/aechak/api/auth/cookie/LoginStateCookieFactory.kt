package com.aechak.api.auth.cookie

import com.aechak.application.auth.service.WebLoginPolicy
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.server.Cookie
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * 로그인 state 쿠키(브라우저 바인딩) — 콜백이 "이 브라우저가 시작한 로그인"인지 증명하는 이중 제출용.
 * Redis(1회성·수명)와 검증 축이 다르다: Redis는 서버 발급 여부를, 쿠키는 시작 주체(브라우저)를 증명한다.
 * 쿠키가 없으면 공격자가 자기 로그인으로 확보한 유효 state를 피해자에게 밟게 하는 로그인 CSRF가 성립한다.
 *
 * - SameSite=Lax 필수 — 콜백은 provider발 cross-site 최상위 GET이라 Strict 쿠키는 그 요청에 실리지 않는다.
 * - Max-Age는 state TTL과 동일, Path는 auth 계열 한정.
 */
@Component
class LoginStateCookieFactory(
    private val policy: WebLoginPolicy,
    @param:Value("\${api.base-path}") private val basePath: String,
) {
    fun issue(
        state: String,
        request: HttpServletRequest,
    ): ResponseCookie = builder(state, request).maxAge(policy.stateTtl).build()

    /** 콜백 처리 후 파기 — state는 1회용이므로 쿠키도 함께 소멸시킨다. */
    fun expire(request: HttpServletRequest): ResponseCookie = builder("", request).maxAge(Duration.ZERO).build()

    private fun builder(
        value: String,
        request: HttpServletRequest,
    ): ResponseCookie.ResponseCookieBuilder =
        ResponseCookie
            .from(LOGIN_STATE_COOKIE, value)
            .httpOnly(true)
            .secure(request.isSecure)
            .sameSite(Cookie.SameSite.LAX.attributeValue())
            .path("$basePath/auth")

    companion object {
        const val LOGIN_STATE_COOKIE = "login_state"
    }
}
