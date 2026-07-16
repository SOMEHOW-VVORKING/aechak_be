package com.aechak.application.auth.port

import com.aechak.domain.user.social.enums.SocialProvider
import com.aechak.domain.user.social.vo.ProviderUser

/**
 * 소셜 id_token 검증 아웃바운드 포트 — 구현은 infra:social-client.
 * 검증(서명·iss·aud·exp) 실패는 BusinessException(AuthErrorCode.INVALID_SOCIAL_TOKEN),
 * 미구성 provider는 UNSUPPORTED_PROVIDER로 번역해 던진다.
 *
 * 포트인 이유(팀 원칙): 이 기술 앞의 유스케이스 로직(회원 연결 분기·완충)이 테스트할 가치가 있다.
 */
interface SocialTokenVerifier {
    fun verify(
        provider: SocialProvider,
        idToken: String,
    ): ProviderUser
}
