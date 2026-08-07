package com.aechak.admin.security;

import com.aechak.application.auth.error.AuthErrorCode;
import com.aechak.application.auth.port.UserStatusReader;
import com.aechak.webcommon.error.ErrorResponse;
import com.aechak.websecurity.authentication.AuthPrincipalConverter;
import com.aechak.websecurity.filter.UserStatusFilter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper; // Boot 4 = Jackson 3 (com.fasterxml → tools.jackson)

/**
 * 어드민 보안 조립: api와 같은 stateless resource server(자체 RS256 JWT 검증)지만,
 * 게이트가 경로 규칙이 아니라 모듈 경계다 — 헬스체크·문서를 뺀 전 요청이 role=ADMIN 전용.
 *
 * <ul>
 *   <li>401(20004): Security 필터 구간이라 @RestControllerAdvice 밖 — EntryPoint가 직접 실패 봉투를 쓴다.
 *   <li>403(20011): hasRole 인가 실패는 ExceptionTranslationFilter가 받는다 — AccessDeniedHandler가 직접 쓴다.
 *   <li>403(20005/20006): 서명검증 뒤 UserStatusFilter가 users.status를 조회해 직접 쓴다.
 *       PENDING_ONBOARDING 허용 경로는 없다 — 어드민에 온보딩 개념이 없다.
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain adminFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            AuthenticationEntryPoint unauthenticatedEntryPoint,
            AccessDeniedHandler forbiddenAccessDeniedHandler,
            UserStatusReader userStatusReader,
            ObjectMapper objectMapper)
            throws Exception {
        http
                .cors(cors -> {}) // corsConfigurationSource 빈 자동 적용
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                // springdoc — 접두(api.base-path) 미부착 경로(우리 컨트롤러가 아님). prod는 springdoc 자체 비활성
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**")
                        .permitAll()
                        .requestMatchers("/actuator/health")
                        .permitAll()
                        .anyRequest()
                        .hasRole("ADMIN"))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(new AuthPrincipalConverter()))
                        .authenticationEntryPoint(unauthenticatedEntryPoint))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(unauthenticatedEntryPoint)
                        .accessDeniedHandler(forbiddenAccessDeniedHandler))
                .addFilterAfter(
                        new UserStatusFilter(userStatusReader, objectMapper, Set.of()),
                        BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 어드민 웹 FE 허용 origin — 값은 환경 설정(api.cors-allowed-origins, 쉼표 구분).
     * 비어 있으면 사실상 비활성(허용 origin 0개). 어드민은 bearer 주 인증이라 쿠키 왕복이 없지만,
     * 정책 형태는 api와 동일하게 유지한다.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${api.cors-allowed-origins:}") String allowedOriginsProperty) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(allowedOriginsProperty.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        config.setAllowedHeaders(List.of("*"));
        config.setMaxAge(3600L); // preflight 캐시(초) — OPTIONS 왕복 절감
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public AuthenticationEntryPoint unauthenticatedEntryPoint(ObjectMapper objectMapper) {
        return (request, response, authException) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter()
                    .write(objectMapper.writeValueAsString(ErrorResponse.Companion.of(AuthErrorCode.UNAUTHENTICATED)));
        };
    }

    @Bean
    public AccessDeniedHandler forbiddenAccessDeniedHandler(ObjectMapper objectMapper) {
        return (request, response, accessDeniedException) -> {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter()
                    .write(objectMapper.writeValueAsString(ErrorResponse.Companion.of(AuthErrorCode.FORBIDDEN)));
        };
    }
}
