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
import tools.jackson.databind.ObjectMapper // Boot 4 = Jackson 3 (com.fasterxml → tools.jackson)

/**
 * API 보안 조립: stateless resource server(자체 RS256 JWT 검증) + 상태검증 필터.
 * "누가 무엇을 쓸 수 있나"(permitAll·401·403·상태 게이트)는 전부 이 파일에서 읽히도록 응집한다.
 *
 * - permitAll: 로그인·토큰 갱신·actuator health(ALB 헬스체크)·API 문서(swagger — prod는 springdoc 자체 비활성). 로그아웃은 인증 필요.
 * - 401(20005): Security 필터 구간이라 @RestControllerAdvice 밖 — EntryPoint가 직접 실패 봉투를 쓴다.
 * - 403(20006/20007): 서명검증 뒤 UserStatusFilter가 users.status를 조회해 직접 쓴다.
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
                        // springdoc — 접두(api.base-path) 미부착 경로(우리 컨트롤러가 아님)
                        "/swagger-ui.html",      // 진입점 (실제론 /swagger-ui/index.html로 redirect)
                        "/swagger-ui/**",        // UI 정적 리소스 (JS/CSS)
                        "/v3/api-docs",          // OpenAPI JSON 본체
                        "/v3/api-docs/**",       // swagger-config, 그룹별 스펙 등
                    ).permitAll()
                    .requestMatchers("/actuator/health")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "$basePath/products")
                    .permitAll()
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
                UserStatusFilter(userStatusReader, objectMapper, onboardingAllowedPaths(basePath)),
                BearerTokenAuthenticationFilter::class.java,
            )
        return http.build()
    }

    /** PENDING_ONBOARDING 허용 경로 — permitAll과 함께, 인가 정책은 이 파일이 전부다. */
    private fun onboardingAllowedPaths(basePath: String): Set<String> =
        setOf(
            "$basePath/users/me",
            "$basePath/users/me/consents",
            "$basePath/users/me/nickname",
            "$basePath/users/nickname/check",
            "$basePath/terms",
            "$basePath/breeds",
            "$basePath/products",
            "$basePath/auth/logout",
            "$basePath/auth/web/refresh",   // PENDING 유저가 Authorization을 붙여 불러도 세션 유지·이탈은 허용
            "$basePath/auth/web/logout",
        )

    /**
     * 브라우저 클라이언트(웹 FE·어드민) 허용 origin — 값은 환경 설정(api.cors-allowed-origins, 쉼표 구분).
     * 비어 있으면 사실상 비활성(허용 origin 0개).
     * allowCredentials=true인 이유: 웹 채널 refresh가 httpOnly 쿠키로 오가므로
     * cross-origin fetch(credentials:'include')에 쿠키 왕복을 허용해야 한다. 와일드카드 origin 금지 제약은
     * 명시 리스트라 충족. CSRF는 SameSite=Lax + 쿠키 Path 한정 + bearer 주 인증으로 방어.
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
                allowCredentials = true // 웹 채널 refresh 쿠키 왕복 — FE는 fetch에 credentials:'include'
                maxAge = 3600 // preflight 캐시(초) — OPTIONS 왕복 절감
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
