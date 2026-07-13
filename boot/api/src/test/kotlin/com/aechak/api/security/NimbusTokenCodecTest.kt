package com.aechak.api.security

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** RS256 인코딩/디코딩 왕복·결정성(멱등 재발급 전제)·refresh형 토큰의 API 인가 거부 검증. */
class NimbusTokenCodecTest {

    private val rsaKey: RSAKey = generateKey()
    private val codec = NimbusTokenCodec(NimbusJwtEncoder(ImmutableJWKSet(JWKSet(rsaKey))), rsaKey)

    @Test
    fun `refresh 토큰 왕복 - 클레임이 보존된다`() {
        val issuedAt = now()
        val expiresAt = issuedAt.plus(30, ChronoUnit.DAYS)
        val token = codec.encodeRefreshToken(7L, "GENERAL", "jti-1", issuedAt, expiresAt)

        val claims = assertNotNull(codec.decodeRefreshToken(token))

        assertEquals(7L, claims.userId)
        assertEquals("GENERAL", claims.role)
        assertEquals("jti-1", claims.tokenId)
        assertEquals(issuedAt, claims.issuedAt)
        assertEquals(expiresAt, claims.expiresAt)
    }

    @Test
    fun `결정적 서명 - 동일 클레임 인코딩은 동일 토큰 문자열이다(유예 멱등 재발급 전제)`() {
        val issuedAt = now()
        val expiresAt = issuedAt.plus(30, ChronoUnit.DAYS)

        val first = codec.encodeRefreshToken(7L, "GENERAL", "jti-1", issuedAt, expiresAt)
        val second = codec.encodeRefreshToken(7L, "GENERAL", "jti-1", issuedAt, expiresAt)

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
        val refreshToken = codec.encodeRefreshToken(7L, "GENERAL", "jti-1", issuedAt, issuedAt.plus(30, ChronoUnit.DAYS))
        val accessToken = codec.encodeAccessToken(7L, "GENERAL", issuedAt, issuedAt.plus(30, ChronoUnit.MINUTES))

        assertFailsWith<JwtException> { apiDecoder.decode(refreshToken) }
        assertEquals("7", apiDecoder.decode(accessToken).subject)
    }

    @Test
    fun `만료된 refresh 토큰은 null`() {
        val issuedAt = now().minus(31, ChronoUnit.DAYS)
        val token = codec.encodeRefreshToken(7L, "GENERAL", "jti-1", issuedAt, issuedAt.plus(30, ChronoUnit.DAYS))

        assertNull(codec.decodeRefreshToken(token))
    }

    private fun now(): Instant = Instant.now().truncatedTo(ChronoUnit.SECONDS)

    private fun generateKey(): RSAKey {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        return RSAKey.Builder(keyPair.public as RSAPublicKey)
            .privateKey(keyPair.private as RSAPrivateKey)
            .keyID("test-key")
            .build()
    }
}
