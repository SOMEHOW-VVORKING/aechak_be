package com.aechak.api.security

import com.aechak.application.auth.error.AuthErrorCode
import com.aechak.application.auth.port.UserStatusReader
import com.aechak.webcommon.error.ErrorResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import tools.jackson.databind.ObjectMapper // Boot 4 = Jackson 3 (com.fasterxml → tools.jackson)

/**
 * API 보안 조립: stateless resource server(자체 RS256 JWT 검증) + 상태검증 필터.
 * "누가 무엇을 쓸 수 있나"(permitAll·401·403·상태 게이트)는 전부 이 파일에서 읽히도록 응집한다.
 *
 * - permitAll: 로그인·토큰 갱신만. 로그아웃은 인증 필요.
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
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it
                    .requestMatchers("$basePath/auth/login/**", "$basePath/auth/token/refresh")
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            }.oauth2ResourceServer { resourceServer ->
                resourceServer.jwt { it.decoder(jwtDecoder) }
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
            "$basePath/auth/logout",
        )

    @Bean
    fun unauthenticatedEntryPoint(objectMapper: ObjectMapper): AuthenticationEntryPoint =
        AuthenticationEntryPoint { _, response, _ ->
            response.status = HttpStatus.UNAUTHORIZED.value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.characterEncoding = Charsets.UTF_8.name()
            response.writer.write(objectMapper.writeValueAsString(ErrorResponse.of(AuthErrorCode.UNAUTHENTICATED)))
        }
}
