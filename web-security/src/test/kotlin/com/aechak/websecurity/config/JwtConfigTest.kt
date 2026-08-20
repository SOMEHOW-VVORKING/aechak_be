package com.aechak.websecurity.config

import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import java.security.KeyPairGenerator
import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/** 키 로드 분기 계약 — 특히 public-only(검증 전용) 모드: 다른 모듈이 발급한 토큰을 서명 키 없이 검증한다. */
class JwtConfigTest {
    private val config = JwtConfig()
    private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    private fun pem(
        header: String,
        encoded: ByteArray,
    ): String = "-----BEGIN $header-----\n${Base64.getMimeEncoder().encodeToString(encoded)}\n-----END $header-----"

    private val publicPem = pem("PUBLIC KEY", keyPair.public.encoded)
    private val privatePem = pem("PRIVATE KEY", keyPair.private.encoded)

    @Test
    fun `public-only 모드 - 서명 키 없이 다른 프로세스가 발급한 토큰을 검증한다`() {
        // 발급자(api 역할): 전체 키
        val issuerKey = config.rsaKey(JwtKeyProperties(privateKey = privatePem, publicKey = publicPem))
        val token =
            config
                .jwtEncoder(issuerKey)
                .encode(
                    JwtEncoderParameters.from(
                        JwtClaimsSet
                            .builder()
                            .subject("7")
                            .issuedAt(Instant.now())
                            .expiresAt(Instant.now().plusSeconds(60))
                            .build(),
                    ),
                ).tokenValue

        // 검증자(seller 역할): 공개키만
        val verifierKey = config.rsaKey(JwtKeyProperties(privateKey = "", publicKey = publicPem))
        assertFalse(verifierKey.isPrivate, "검증 전용 키는 서명 재료를 갖지 않아야 한다")

        assertEquals("7", config.jwtDecoder(verifierKey).decode(token).subject)
    }

    @Test
    fun `private만 있는 설정은 부팅 실패다 - 조용한 임시 키 폴백 금지`() {
        assertFailsWith<IllegalStateException> {
            config.rsaKey(JwtKeyProperties(privateKey = privatePem, publicKey = ""))
        }
    }

    @Test
    fun `둘 다 비면 임시 키 - 기존 로컬 동작 유지`() {
        val key = config.rsaKey(JwtKeyProperties())

        assertEquals("local-ephemeral", key.keyID)
    }
}
