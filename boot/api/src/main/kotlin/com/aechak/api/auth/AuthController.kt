package com.aechak.api.auth

import com.aechak.api.auth.request.AppleLoginRequest
import com.aechak.api.auth.request.KakaoLoginRequest
import com.aechak.api.auth.request.LogoutRequest
import com.aechak.api.auth.request.RefreshTokenRequest
import com.aechak.api.auth.response.LoginResponse
import com.aechak.api.auth.response.TokenPairResponse
import com.aechak.application.auth.usecase.LogoutUseCase
import com.aechak.application.auth.usecase.SocialLoginUseCase
import com.aechak.application.auth.usecase.TokenRefreshUseCase
import com.aechak.webcommon.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * auth API — 컨트롤러는 형식 검증과 Request→Command / Result→Response 변환만 한다.
 * 탈퇴(DELETE /users/me)는 회원 탈퇴 기능에서 추가한다.
 */
@RestController
@RequestMapping("/auth") // 접두(api.base-path)는 WebConfig가 일괄 부착
class AuthController(
    private val socialLoginUseCase: SocialLoginUseCase,
    private val tokenRefreshUseCase: TokenRefreshUseCase,
    private val logoutUseCase: LogoutUseCase,
) {
    @PostMapping("/login/kakao")
    fun loginKakao(
        @Valid @RequestBody request: KakaoLoginRequest,
    ): ResponseEntity<ApiResponse<LoginResponse>> =
        ResponseEntity.ok(ApiResponse.of(LoginResponse.from(socialLoginUseCase.login(request.toCommand()))))

    @PostMapping("/login/apple")
    fun loginApple(
        @Valid @RequestBody request: AppleLoginRequest,
    ): ResponseEntity<ApiResponse<LoginResponse>> =
        ResponseEntity.ok(ApiResponse.of(LoginResponse.from(socialLoginUseCase.login(request.toCommand()))))

    @PostMapping("/token/refresh")
    fun refresh(
        @Valid @RequestBody request: RefreshTokenRequest,
    ): ResponseEntity<ApiResponse<TokenPairResponse>> =
        ResponseEntity.ok(ApiResponse.of(TokenPairResponse.from(tokenRefreshUseCase.refresh(request.refreshToken))))

    @PostMapping("/logout")
    fun logout(
        @Valid @RequestBody request: LogoutRequest,
    ): ResponseEntity<Void> {
        logoutUseCase.logout(request.refreshToken)
        return ResponseEntity.noContent().build()
    }
}
