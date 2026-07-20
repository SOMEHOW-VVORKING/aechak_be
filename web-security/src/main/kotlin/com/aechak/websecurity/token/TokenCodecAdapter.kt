package com.aechak.websecurity.token

import com.aechak.application.auth.port.RefreshTokenClaims
import com.aechak.application.auth.port.TokenCodec
import com.aechak.websecurity.config.JwtConfig
import com.nimbusds.jose.jwk.RSAKey
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * TokenCodec 포트의 Nimbus(Spring Security oauth2-jose) 어댑터 — Spring Security 타입을 application 밖(boot:api)에 격리한다.
 * RS256 서명은 결정적이라 동일 클레임 인코딩 = 동일 토큰 — 회전 유예의 멱등 재발급이 성립하는 전제다.
 */
@Component
class TokenCodecAdapter(
    private val jwtEncoder: JwtEncoder,
    rsaKey: RSAKey,
) : TokenCodec {
    /** refresh 전용 디코더 — 서명·exp만 검증하고 token_type은 아래서 직접 확인한다. */
    private val refreshDecoder: JwtDecoder =
        NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build().apply {
            setJwtValidator(JwtValidators.createDefault())
        }

    override fun encodeAccessToken(
        userId: Long,
        role: String,
        issuedAt: Instant,
        expiresAt: Instant,
    ): String =
        encode(
            JwtClaimsSet
                .builder()
                .subject(userId.toString())
                .claim(ROLE_CLAIM, role)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build(),
        )

    override fun encodeRefreshToken(
        userId: Long,
        role: String,
        tokenId: String,
        issuedAt: Instant,
        expiresAt: Instant,
    ): String =
        encode(
            JwtClaimsSet
                .builder()
                .subject(userId.toString())
                .claim(ROLE_CLAIM, role)
                .id(tokenId)
                .claim(JwtConfig.TOKEN_TYPE_CLAIM, JwtConfig.REFRESH_TOKEN_TYPE)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build(),
        )

    override fun decodeRefreshToken(token: String): RefreshTokenClaims? {
        val jwt =
            try {
                refreshDecoder.decode(token)
            } catch (e: JwtException) {
                return null
            }
        if (jwt.getClaimAsString(JwtConfig.TOKEN_TYPE_CLAIM) != JwtConfig.REFRESH_TOKEN_TYPE) return null

        return RefreshTokenClaims(
            userId = jwt.subject?.toLongOrNull() ?: return null,
            role = jwt.getClaimAsString(ROLE_CLAIM) ?: return null,
            tokenId = jwt.id ?: return null,
            issuedAt = jwt.issuedAt ?: return null,
            expiresAt = jwt.expiresAt ?: return null,
        )
    }

    private fun encode(claims: JwtClaimsSet): String = jwtEncoder.encode(JwtEncoderParameters.from(claims)).tokenValue

    companion object {
        private const val ROLE_CLAIM = "role"
    }
}
