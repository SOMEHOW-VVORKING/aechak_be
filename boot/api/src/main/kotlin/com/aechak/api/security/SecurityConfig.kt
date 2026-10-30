package com.aechak.api.security

import com.aechak.application.auth.error.AuthErrorCode
import com.aechak.application.auth.port.UserStatusReader
import com.aechak.webcommon.error.ErrorResponse
import com.aechak.websecurity.authentication.AuthPrincipalConverter
import com.aechak.websecurity.filter.UserStatusFilter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import tools.jackson.databind.ObjectMapper // Spring Boot 4부터 Jackson 3의 tools.jackson 패키지를 사용한다.

/**
 * RS256 JWT를 검증하는 stateless resource server와 사용자 상태 필터를 설정한다.
 * 공개 경로와 인증 실패 응답, 사용자 상태별 접근 권한을 이 파일에서 관리한다.
 *
 * 로그인, 토큰 갱신, 헬스 체크, API 문서는 인증 없이 접근할 수 있다. 일반 로그아웃은 인증이 필요하다.
 * 결제 웹훅(/webhooks/portone)도 인증 없이 받는다 — 신원은 JWT가 아니라 포트원 서명 검증이 보증한다.
 * JWT 인증 실패는 AuthenticationEntryPoint가 401로 응답하고, 계정 상태에 따른 제한은 UserStatusFilter가 403으로 응답한다.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
class SecurityConfig {
    @Bean
    fun apiFilterChain(
        http: HttpSecurity,
        jwtDecoder: JwtDecoder,
        unauthenticatedEntryPoint: AuthenticationEntryPoint,
        userStatusReader: UserStatusReader,
        objectMapper: ObjectMapper,
        @Value("\${api.base-path}") basePath: String,
    ): SecurityFilterChain {
        http
            .cors { } // corsConfigurationSource 빈 자동 적용
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it
                    .requestMatchers(
                        "$basePath/auth/login/**",      // body 로그인 + 웹 로그인 진입(login/{provider}/redirect)
                        "$basePath/auth/refresh",       // body 채널 갱신
                        "$basePath/auth/callback/**",   // provider → 서버 콜백 (state가 방어선)
                        "$basePath/auth/web/refresh",   // 쿠키 채널 갱신 (쿠키가 자격증명)
                        "$basePath/auth/web/logout",    // 쿠키 채널 로그아웃 (쿠키가 자격증명, 멱등)
                    ).permitAll()
                    .requestMatchers(
                        "/swagger-ui.html",      // 진입점 (실제론 /swagger-ui/index.html로 redirect)
                        "/swagger-ui/**",        // UI 정적 리소스 (JS/CSS)
                        "/v3/api-docs",          // OpenAPI JSON
                        "/v3/api-docs/**",       // swagger-config, 그룹별 스펙 등
                    ).permitAll()
                    .requestMatchers("/actuator/health")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "$basePath/webhooks/portone") // 신원은 JWT가 아니라 포트원 서명이 보증
                    .permitAll()
                    // 게스트 카탈로그. 와일드카드는 세그먼트 한 칸이라 상품 상세는 열리고,
                    // 로그인을 요구하는 /products/{productId}/reviews 는 닫힌 채로 남는다.
                    // 홈은 아직 비회원에게 열지 않아 여기에 넣지 않는다.
                    .requestMatchers(
                        HttpMethod.GET,
                        "$basePath/products",
                        "$basePath/products/*",
                        "$basePath/products/*/options",
                        "$basePath/search/products",
                        "$basePath/sns/users/*", // 한 칸 와일드카드라 /followers, /followings는 닫힌 채로 남는다
                    ).permitAll()
                    .anyRequest()
                    .authenticated()
            }.oauth2ResourceServer { resourceServer ->
                resourceServer.jwt {
                    it.decoder(jwtDecoder)
                    it.jwtAuthenticationConverter(AuthPrincipalConverter())
                }
                resourceServer.authenticationEntryPoint(unauthenticatedEntryPoint)
            }.exceptionHandling { it.authenticationEntryPoint(unauthenticatedEntryPoint) }
            .addFilterAfter(
                UserStatusFilter(
                    userStatusReader,
                    objectMapper,
                    onboardingAllowedPaths(basePath),
                    restrictedWritePathPrefixes(basePath),
                ),
                BearerTokenAuthenticationFilter::class.java,
            )
        return http.build()
    }

    /** 온보딩을 마치지 않은 사용자(PENDING_ONBOARDING)에게 허용할 경로 */
    private fun onboardingAllowedPaths(basePath: String): Set<String> =
        setOf(
            "$basePath/users/me",
            "$basePath/users/me/withdrawal/check",
            "$basePath/users/me/consents",
            "$basePath/users/me/nickname",
            "$basePath/users/nickname/check",
            "$basePath/terms",
            "$basePath/breeds",
            "$basePath/products",
            "$basePath/auth/logout",
            "$basePath/auth/web/refresh",
            "$basePath/auth/web/logout",
        )

    /**
     * 이용 제한(RESTRICTED) 계정에게 막을 경로 접두 — 쓰기 메서드에만 적용된다.
     *
     * 화면이 약속한 제한 범위는 "일부 기능(리뷰 작성, 채팅 등)"이다. 채팅은 아직 없으므로
     * 지금 존재하는 커뮤니티성 쓰기만 담는다 — 리뷰 작성/삭제, SNS 활동, 문의 등록.
     * 주문·결제는 뺀다: 이용 제한은 거래를 끊는 제재가 아니고, 거래를 끊어야 하는 제재는 임시/영구 정지다.
     */
    private fun restrictedWritePathPrefixes(basePath: String): Set<String> =
        setOf(
            "$basePath/reviews",
            "$basePath/sns",
            "$basePath/inquiries",
        )

    /**
     * 웹과 관리자 클라이언트에서 허용할 origin을 api.cors-allowed-origins 설정으로 받는다.
     * 값이 비어 있으면 어떤 origin도 허용하지 않는다.
     * 웹 토큰 갱신에 httpOnly 쿠키를 사용하므로 allowCredentials를 활성화한다.
     * origin을 명시적으로 제한하고 SameSite=Lax, 쿠키 Path, bearer 인증을 함께 사용한다.
     */
    @Bean
    fun corsConfigurationSource(
        @Value("\${api.cors-allowed-origins:}") allowedOriginsProperty: String,
    ): CorsConfigurationSource {
        val config =
            CorsConfiguration().apply {
                allowedOrigins = allowedOriginsProperty.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE")
                allowedHeaders = listOf("*")
                allowCredentials = true // 웹 클라이언트는 fetch 요청에 credentials: 'include'를 사용한다.
                maxAge = 3600 // preflight 결과를 캐시할 시간(초)
            }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", config)
        }
    }

    @Bean
    fun unauthenticatedEntryPoint(objectMapper: ObjectMapper): AuthenticationEntryPoint =
        AuthenticationEntryPoint { _, response, _ ->
            response.status = HttpStatus.UNAUTHORIZED.value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.characterEncoding = Charsets.UTF_8.name()
            response.writer.write(objectMapper.writeValueAsString(ErrorResponse.of(AuthErrorCode.UNAUTHENTICATED)))
        }
}
