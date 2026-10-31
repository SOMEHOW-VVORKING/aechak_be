package com.aechak.websecurity.token

import com.aechak.websecurity.config.JwtConfig
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** RS256 인코딩/디코딩 왕복·결정성(멱등 재발급 전제)·refresh형 토큰의 API 인가 거부 검증. */
class TokenCodecAdapterTest {
    private val rsaKey: RSAKey = generateKey()
    private val encoder = NimbusJwtEncoder(ImmutableJWKSet(JWKSet(rsaKey)))
    private val codec = TokenCodecAdapter(encoder, rsaKey)

    @Test
    fun `refresh 토큰 왕복 - 클레임이 보존된다`() {
        val issuedAt = now()
        val expiresAt = issuedAt.plus(30, ChronoUnit.DAYS)
        val token = codec.encodeRefreshToken(7L, "jti-1", issuedAt, expiresAt)

        val claims = assertNotNull(codec.decodeRefreshToken(token))

        assertEquals(7L, claims.userId)
        assertEquals("jti-1", claims.tokenId)
        assertEquals(issuedAt, claims.issuedAt)
        assertEquals(expiresAt, claims.expiresAt)
        // 역할이 서명 입력으로 돌아오면 유예 재발급이 다시 바이트가 달라진다.
        assertFalse(payloadOf(token).contains(JwtConfig.ROLE_CLAIM), payloadOf(token))
    }

    @Test
    fun `역할이 박힌 옛 refresh 토큰도 그대로 디코딩된다(하위 호환)`() {
        val issuedAt = now()
        val expiresAt = issuedAt.plus(30, ChronoUnit.DAYS)
        val legacyToken = legacyRefreshToken(issuedAt, expiresAt)

        val claims = assertNotNull(codec.decodeRefreshToken(legacyToken))

        // 배포 시점에 살아 있는 사슬이 끊기지 않아야 한다 — 디코딩이 역할을 읽지 않으므로 통과한다.
        assertEquals(7L, claims.userId)
        assertEquals("jti-legacy", claims.tokenId)
        assertEquals(issuedAt, claims.issuedAt)
        assertEquals(expiresAt, claims.expiresAt)
    }

    @Test
    fun `결정적 서명 - 동일 클레임 인코딩은 동일 토큰 문자열이다(유예 멱등 재발급 전제)`() {
        val issuedAt = now()
        val expiresAt = issuedAt.plus(30, ChronoUnit.DAYS)

        val first = codec.encodeRefreshToken(7L, "jti-1", issuedAt, expiresAt)
        val second = codec.encodeRefreshToken(7L, "jti-1", issuedAt, expiresAt)

        assertEquals(first, second)
    }

    @Test
    fun `access 토큰은 refresh 디코더가 거부한다(token_type 부재)`() {
        val issuedAt = now()
        val token = codec.encodeAccessToken(7L, "GENERAL", issuedAt, issuedAt.plus(30, ChronoUnit.MINUTES))

        assertNull(codec.decodeRefreshToken(token))
    }

    @Test
    fun `API 인가 디코더는 refresh형 토큰을 거부한다`() {
        val apiDecoder = JwtConfig().jwtDecoder(rsaKey)
        val issuedAt = now()
        val refreshToken = codec.encodeRefreshToken(7L, "jti-1", issuedAt, issuedAt.plus(30, ChronoUnit.DAYS))
        val accessToken = codec.encodeAccessToken(7L, "GENERAL", issuedAt, issuedAt.plus(30, ChronoUnit.MINUTES))

        assertFailsWith<JwtException> { apiDecoder.decode(refreshToken) }
        assertEquals("7", apiDecoder.decode(accessToken).subject)
    }

    @Test
    fun `만료된 refresh 토큰은 null`() {
        val issuedAt = now().minus(31, ChronoUnit.DAYS)
        val token = codec.encodeRefreshToken(7L, "jti-1", issuedAt, issuedAt.plus(30, ChronoUnit.DAYS))

        assertNull(codec.decodeRefreshToken(token))
    }

    /** 이번 변경 전 코드가 굽던 모양 — role 클레임이 들어 있는 refresh. */
    private fun legacyRefreshToken(
        issuedAt: Instant,
        expiresAt: Instant,
    ): String =
        encoder
            .encode(
                JwtEncoderParameters.from(
                    JwtClaimsSet
                        .builder()
                        .subject("7")
                        .claim(JwtConfig.ROLE_CLAIM, "ADMIN")
                        .id("jti-legacy")
                        .claim(JwtConfig.TOKEN_TYPE_CLAIM, JwtConfig.REFRESH_TOKEN_TYPE)
                        .issuedAt(issuedAt)
                        .expiresAt(expiresAt)
                        .build(),
                ),
            ).tokenValue

    private fun payloadOf(token: String): String = String(Base64.getUrlDecoder().decode(token.split(".")[1]))

    private fun now(): Instant = Instant.now().truncatedTo(ChronoUnit.SECONDS)

    private fun generateKey(): RSAKey {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        return RSAKey
            .Builder(keyPair.public as RSAPublicKey)
            .privateKey(keyPair.private as RSAPrivateKey)
            .keyID("test-key")
            .build()
    }
}
