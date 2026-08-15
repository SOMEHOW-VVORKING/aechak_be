package com.aechak.seller.security

import com.aechak.application.auth.error.AuthErrorCode
import com.aechak.application.auth.port.UserStatusReader
import com.aechak.webcommon.error.ErrorResponse
import com.aechak.websecurity.authentication.AuthPrincipalConverter
import com.aechak.websecurity.filter.UserStatusFilter
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
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import tools.jackson.databind.ObjectMapper // Boot 4 = Jackson 3 (com.fasterxml → tools.jackson)

/**
 * 셀러센터 보안 조립: stateless resource server(자체 RS256 JWT 검증) + 상태검증 필터.
 * api와 다른 점 — 로그인·토큰 발급 EP가 없고(발급은 api 소관, 여긴 검증만),
 * 온보딩 허용 경로도 없다(셀러센터 전 EP가 ACTIVE 전용).
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
class SecurityConfig {
    @Bean
    fun sellerFilterChain(
        http: HttpSecurity,
        jwtDecoder: JwtDecoder,
        unauthenticatedEntryPoint: AuthenticationEntryPoint,
        userStatusReader: UserStatusReader,
        objectMapper: ObjectMapper,
    ): SecurityFilterChain {
        http
            .cors { } // corsConfigurationSource 빈 자동 적용
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it
                    .requestMatchers(
                        // springdoc — 접두(api.base-path) 미부착 경로. prod는 springdoc 자체 비활성 전제(api와 동일)
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                    ).permitAll()
                    .requestMatchers("/actuator/health")
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
                UserStatusFilter(userStatusReader, objectMapper, emptySet()),
                BearerTokenAuthenticationFilter::class.java,
            )
        return http.build()
    }

    /** 셀러센터 웹 FE 허용 origin — 값은 환경 설정(api.cors-allowed-origins, 쉼표 구분). 비어 있으면 사실상 비활성. */
    @Bean
    fun corsConfigurationSource(
        @Value("\${api.cors-allowed-origins:}") allowedOriginsProperty: String,
    ): CorsConfigurationSource {
        val config =
            CorsConfiguration().apply {
                allowedOrigins = allowedOriginsProperty.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE")
                allowedHeaders = listOf("*")
                allowCredentials = true
                maxAge = 3600
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
