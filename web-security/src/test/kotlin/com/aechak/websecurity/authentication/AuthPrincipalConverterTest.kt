package com.aechak.websecurity.authentication

import com.aechak.domain.user.user.enums.UserRole
import com.aechak.websecurity.config.JwtConfig
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/** Jwt → AuthPrincipal 변환: sub·role 클레임 매핑과 hasRole용 ROLE_ 권한 부여 검증. */
class AuthPrincipalConverterTest {
    private val converter = AuthPrincipalConverter()

    @Test
    fun `sub와 role 클레임이 AuthPrincipal로 매핑된다`() {
        val authentication = converter.convert(jwt(subject = "7", role = "ADMIN"))

        val principal = authentication.principal as AuthPrincipal
        assertEquals(7L, principal.userId)
        assertEquals(UserRole.ADMIN, principal.role)
    }

    @Test
    fun `role은 ROLE_ 접두 권한으로 부여된다 - hasRole 매칭 전제`() {
        val authentication = converter.convert(jwt(subject = "7", role = "GENERAL"))

        assertEquals(setOf("ROLE_GENERAL"), authentication.authorities.map { it.authority }.toSet())
        assertEquals(true, authentication.isAuthenticated)
    }

    private fun jwt(
        subject: String,
        role: String,
    ): Jwt =
        Jwt
            .withTokenValue("token")
            .header("alg", "RS256")
            .subject(subject)
            .claim(JwtConfig.ROLE_CLAIM, role)
            .issuedAt(Instant.EPOCH)
            .expiresAt(Instant.EPOCH.plusSeconds(1800))
            .build()
}
