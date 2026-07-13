package com.aechak.infra.social

import com.aechak.application.auth.error.AuthErrorCode
import com.aechak.application.auth.port.SocialTokenVerifier
import com.aechak.common.error.BusinessException
import com.aechak.domain.user.social.vo.ProviderUser
import com.aechak.domain.user.social.enums.SocialProvider
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.stereotype.Component

/**
 * SocialTokenVerifier 포트 구현 — provider별 JwtDecoder로 라우팅한다.
 * 카카오·애플 검증 규칙 차이는 decoder(validators)에 들어 있어 매핑 코드는 공통이다
 * (design의 Kakao/AppleIdTokenVerifier 부품은 동형이라 어댑터 하나로 접음 — 리뷰 포인트).
 */
@Component
class SocialTokenVerifierAdapter(
    private val socialJwtDecoders: Map<SocialProvider, JwtDecoder>,
) : SocialTokenVerifier {

    override fun verify(provider: SocialProvider, idToken: String): ProviderUser {
        val decoder = socialJwtDecoders[provider]
            ?: throw BusinessException(AuthErrorCode.UNSUPPORTED_PROVIDER)

        val jwt = try {
            decoder.decode(idToken)
        } catch (e: JwtException) {
            throw BusinessException(AuthErrorCode.INVALID_SOCIAL_TOKEN, e)
        }
        return ProviderUser(provider, jwt.subject, jwt.getClaimAsString("email"))
    }
}
