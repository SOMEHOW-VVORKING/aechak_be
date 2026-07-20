package com.aechak.websecurity.authentication

import com.aechak.domain.user.user.enums.UserRole
import com.aechak.websecurity.config.JwtConfig
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt

/**
 * 검증된 Jwt를 AuthPrincipal 주체로 변환 — principal이 Spring Security 타입(Jwt) 대신 도메인 값이 된다.
 * ROLE_ 접두 권한을 심어 SecurityConfig의 hasRole 인가가 성립한다.
 */
class AuthPrincipalConverter : Converter<Jwt, AbstractAuthenticationToken> {
    override fun convert(jwt: Jwt): AbstractAuthenticationToken {
        val role = UserRole.valueOf(jwt.getClaimAsString(JwtConfig.ROLE_CLAIM))
        val principal = AuthPrincipal(jwt.subject.toLong(), role)
        val authorities = listOf(SimpleGrantedAuthority("ROLE_${role.name}"))
        return object : AbstractAuthenticationToken(authorities) {
            override fun getPrincipal() = principal

            override fun getCredentials() = jwt

            init {
                isAuthenticated = true
            }
        }
    }
}
