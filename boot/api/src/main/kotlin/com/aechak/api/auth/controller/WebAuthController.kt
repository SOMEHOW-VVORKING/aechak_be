package com.aechak.api.auth.controller

import com.aechak.api.auth.cookie.LoginStateCookieFactory
import com.aechak.api.auth.cookie.RefreshCookieFactory
import com.aechak.api.auth.response.WebTokenResponse
import com.aechak.application.auth.error.AuthErrorCode
import com.aechak.application.auth.usecase.LogoutUseCase
import com.aechak.application.auth.usecase.TokenRefreshUseCase
import com.aechak.application.auth.usecase.WebLoginUseCase
import com.aechak.application.auth.usecase.result.WebLoginCompletion
import com.aechak.common.error.BusinessException
import com.aechak.domain.user.social.enums.SocialProvider
import com.aechak.webcommon.response.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

/**
 * 웹(브라우저) 채널 전용 컨트롤러 — 쿠키·리다이렉트라는 브라우저 고유물이 웹 채널에 격리된다
 * (쿠키 속성 정책은 cookie 패키지의 팩토리들이 소유).
 * body 채널(AuthController)과 엔드포인트를 분리한 이유: 요청/응답 형태가 채널별로 하나로 고정되어
 * "어느 모드인지" 판별 로직 자체가 필요 없어진다.
 *
 * refresh는 httpOnly 쿠키로만 오간다 — 응답 body에 싣지 않는 것이 계약(JS는 refresh를 만질 수 없다).
 */
@RestController
@RequestMapping("/auth") // 접두(api.base-path)는 WebConfig가 일괄 부착
class WebAuthController(
    private val webLoginUseCase: WebLoginUseCase,
    private val tokenRefreshUseCase: TokenRefreshUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val refreshCookieFactory: RefreshCookieFactory,
    private val loginStateCookieFactory: LoginStateCookieFactory,
) {
    /**
     * 웹 로그인 진입 — provider authorize 화면으로 302. FE는 이 주소로 이동만 하면 된다.
     * state를 쿠키에도 심는다(브라우저 바인딩) — 콜백에서 이중 제출 대조해 로그인 CSRF를 막는다.
     */
    @GetMapping("/login/{provider}/redirect")
    fun redirectToProvider(
        @PathVariable provider: String,
        @RequestParam("return") returnUrl: String,
        request: HttpServletRequest,
    ): ResponseEntity<Void> {
        val preparation = webLoginUseCase.prepareLogin(parseProvider(provider), returnUrl)
        return ResponseEntity
            .status(HttpStatus.FOUND)
            .location(URI.create(preparation.authorizeUrl))
            .header(HttpHeaders.SET_COOKIE, loginStateCookieFactory.issue(preparation.state, request).toString())
            .build()
    }

    /**
     * provider 콜백 — 이 응답에는 body 수신자가 없다(브라우저 주소창이 보낸 요청).
     * 성공/실패 모두 (검증된) return으로 302, refresh는 Set-Cookie로만 전달.
     */
    @GetMapping("/callback/{provider}")
    fun callback(
        @PathVariable provider: String,
        @RequestParam(required = false) code: String?,
        @RequestParam state: String,
        @CookieValue(LoginStateCookieFactory.LOGIN_STATE_COOKIE, required = false) stateCookie: String?,
        request: HttpServletRequest,
    ): ResponseEntity<Void> {
        // 브라우저 바인딩 대조(이중 제출) — 쿠키 부재·불일치 = 이 브라우저가 시작한 로그인이 아니다(로그인 CSRF 차단).
        // 진입 없이 콜백만 밟은 요청이므로 returnUrl도 알 수 없다 → state 소비 실패와 같은 직접 400.
        if (stateCookie != state) throw BusinessException(AuthErrorCode.INVALID_LOGIN_STATE)

        val completion = webLoginUseCase.completeLogin(parseProvider(provider), code, state)

        val redirect =
            when (completion) {
                is WebLoginCompletion.Success -> {
                    UriComponentsBuilder
                        .fromUriString(completion.returnUrl)
                        .queryParam("status", completion.userStatus.name)
                        .queryParam("isNew", completion.isNew)
                        .build()
                        .toUriString()
                }

                is WebLoginCompletion.Failure -> {
                    UriComponentsBuilder
                        .fromUriString(completion.returnUrl)
                        .queryParam("error", completion.errorCode.code)
                        .build()
                        .toUriString()
                }
            }

        val response =
            ResponseEntity
                .status(HttpStatus.FOUND)
                .location(URI.create(redirect))
                .header(HttpHeaders.SET_COOKIE, loginStateCookieFactory.expire(request).toString()) // state는 1회용 — 쿠키도 파기
        if (completion is WebLoginCompletion.Success) {
            // access는 여기 실을 수 없다(수신자 없음·URL은 유출면) — FE가 복귀 후 /auth/web/refresh로 수령
            response.header(HttpHeaders.SET_COOKIE, refreshCookieFactory.issue(completion.refreshToken, request).toString())
        }
        return response.build()
    }

    /** 세션 복원·갱신 — 빈 body, 쿠키가 자격증명. 새 refresh는 다시 Set-Cookie로(회전 = 쿠키 교체). */
    @PostMapping("/web/refresh")
    fun refresh(
        @CookieValue(RefreshCookieFactory.REFRESH_COOKIE, required = false) refreshToken: String?,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<WebTokenResponse>> {
        if (refreshToken == null) throw BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN)
        val result = tokenRefreshUseCase.refresh(refreshToken)
        return ResponseEntity
            .ok()
            .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.issue(result.refreshToken, request).toString())
            .body(ApiResponse.of(WebTokenResponse.from(result)))
    }

    /** 로그아웃 — 서버 세션 폐기 + 쿠키 파기. 쿠키 없으면 이미 로그아웃 상태라 그대로 204(멱등). */
    @PostMapping("/web/logout")
    fun logout(
        @CookieValue(RefreshCookieFactory.REFRESH_COOKIE, required = false) refreshToken: String?,
        request: HttpServletRequest,
    ): ResponseEntity<Void> {
        refreshToken?.let(logoutUseCase::logout)
        return ResponseEntity
            .noContent()
            .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.expire(request).toString())
            .build()
    }

    private fun parseProvider(raw: String): SocialProvider =
        runCatching { SocialProvider.valueOf(raw.uppercase()) }
            .getOrElse { throw BusinessException(AuthErrorCode.UNSUPPORTED_PROVIDER) }
}
