package com.aechak.websecurity.config

import com.aechak.application.auth.service.TokenPolicy
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * 자체 토큰 RS256 조립: 키 로드 → JwtEncoder(발급) / JwtDecoder(API 인가).
 * API 인가 디코더는 token_type=refresh인 JWT를 거부한다 — refresh를 access로 오용하는 것 차단.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthTokenProperties::class, JwtKeyProperties::class)
class JwtConfig {
    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun tokenPolicy(props: AuthTokenProperties): TokenPolicy = TokenPolicy(props.accessTtl, props.refreshTtl, props.rotationGrace)

    @Bean
    fun rsaKey(props: JwtKeyProperties): RSAKey =
        when {
            props.privateKey.isBlank() && props.publicKey.isBlank() -> generateEphemeralKey()

            // 검증 전용 모듈(seller-api 등) — 서명 키를 보유하지 않아 유출돼도 토큰 위조가 불가하다.
            // 이 키로 만든 jwtEncoder 빈은 서명 시점에 실패한다 — 발급 EP가 없는 모듈에서만 쓸 것.
            props.privateKey.isBlank() -> loadPublicOnly(props)

            props.publicKey.isBlank() -> error("auth.jwt.private-key만 설정됨 — public-key 없이는 검증이 불가하다(설정 누락)")

            else -> loadFromPem(props)
        }

    @Bean
    fun jwtEncoder(rsaKey: RSAKey): JwtEncoder = NimbusJwtEncoder(ImmutableJWKSet(JWKSet(rsaKey)))

    @Bean
    fun jwtDecoder(rsaKey: RSAKey): JwtDecoder {
        val decoder = NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build()
        decoder.setJwtValidator(
            DelegatingOAuth2TokenValidator(JwtValidators.createDefault(), rejectRefreshTokens()),
        )
        return decoder
    }

    private fun rejectRefreshTokens(): OAuth2TokenValidator<Jwt> =
        OAuth2TokenValidator { jwt ->
            if (jwt.getClaimAsString(TOKEN_TYPE_CLAIM) == REFRESH_TOKEN_TYPE) {
                OAuth2TokenValidatorResult.failure(
                    OAuth2Error("invalid_token", "refresh token은 API 인가에 쓸 수 없습니다", null),
                )
            } else {
                OAuth2TokenValidatorResult.success()
            }
        }

    private fun generateEphemeralKey(): RSAKey {
        log.warn("auth.jwt 키 미설정 — 개발용 임시 RS256 키를 생성합니다. 재시작 시 기존 토큰이 전부 무효화됩니다.")
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        return RSAKey
            .Builder(keyPair.public as RSAPublicKey)
            .privateKey(keyPair.private as RSAPrivateKey)
            .keyID(EPHEMERAL_KEY_ID)
            .build()
    }

    private fun loadPublicOnly(props: JwtKeyProperties): RSAKey {
        val keyFactory = KeyFactory.getInstance("RSA")
        val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(decodePem(props.publicKey))) as RSAPublicKey
        return RSAKey
            .Builder(publicKey)
            .keyID(CONFIGURED_KEY_ID)
            .build()
    }

    private fun loadFromPem(props: JwtKeyProperties): RSAKey {
        val keyFactory = KeyFactory.getInstance("RSA")
        val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(decodePem(props.publicKey))) as RSAPublicKey
        val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(decodePem(props.privateKey))) as RSAPrivateKey
        return RSAKey
            .Builder(publicKey)
            .privateKey(privateKey)
            .keyID(CONFIGURED_KEY_ID)
            .build()
    }

    private fun decodePem(pem: String): ByteArray =
        Base64.getDecoder().decode(
            pem.replace(Regex("-----[A-Z ]+-----"), "").replace(Regex("\\s"), ""),
        )

    companion object {
        const val TOKEN_TYPE_CLAIM = "token_type"
        const val REFRESH_TOKEN_TYPE = "refresh"
        const val ROLE_CLAIM = "role"
        private const val EPHEMERAL_KEY_ID = "local-ephemeral"

        // TODO: kid 로테이션 절차 확정 시 설정으로 승격
        private const val CONFIGURED_KEY_ID = "aechak-auth-v1"
    }
}
