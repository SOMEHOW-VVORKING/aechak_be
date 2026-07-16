package com.aechak.domain.user.social.vo

import com.aechak.domain.user.social.enums.SocialProvider

/**
 * 검증을 통과한 소셜 신원 값 객체.
 * id_token 검증 어댑터의 출력 — 이 타입이 존재한다는 것 자체가 "서명·발급자·수신자 검증 완료"를 뜻한다.
 */
data class ProviderUser(
    val provider: SocialProvider,
    val providerId: String,
    val email: String?,
)
