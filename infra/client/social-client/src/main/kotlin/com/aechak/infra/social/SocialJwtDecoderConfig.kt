package com.aechak.infra.social

import com.aechak.domain.user.social.enums.SocialProvider
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.web.client.RestTemplate

/**
 * yml(auth.social.providers.*) → provider별 JwtDecoder 지도.
 * 검증 규칙(iss/aud/exp)은 provider마다 다르고 코드 경로는 동일하다.
 * JWKS 페칭 타임아웃은 명시 설정 — 기본값 방치 금지. 키 캐싱은 Nimbus 내장.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SocialProvidersProperties::class)
class SocialJwtDecoderConfig {
    @Bean
    fun socialJwtDecoders(props: SocialProvidersProperties): Map<SocialProvider, JwtDecoder> =
        props.providers.entries.associate { (name, provider) ->
            SocialProvider.valueOf(name.uppercase()) to buildDecoder(provider)
        }

    private fun buildDecoder(props: SocialProvidersProperties.ProviderProperties): JwtDecoder {
        val restTemplate =
            RestTemplate(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(JWKS_TIMEOUT_MS)
                    setReadTimeout(JWKS_TIMEOUT_MS)
                },
            )
        val decoder =
            NimbusJwtDecoder
                .withJwkSetUri(props.jwksUri)
                .restOperations(restTemplate)
                .build()

        val validators =
            buildList {
                add(JwtValidators.createDefaultWithIssuer(props.issuer))
                if (props.audiences.isNotEmpty()) add(audienceValidator(props.audiences))
            }
        decoder.setJwtValidator(DelegatingOAuth2TokenValidator(validators))
        return decoder
    }

    private fun audienceValidator(allowed: List<String>): OAuth2TokenValidator<Jwt> =
        OAuth2TokenValidator { jwt ->
            if (jwt.audience.any { it in allowed }) {
                OAuth2TokenValidatorResult.success()
            } else {
                OAuth2TokenValidatorResult.failure(
                    OAuth2Error("invalid_token", "허용되지 않은 aud 클레임", null),
                )
            }
        }

    companion object {
        private const val JWKS_TIMEOUT_MS = 3_000
    }
}
