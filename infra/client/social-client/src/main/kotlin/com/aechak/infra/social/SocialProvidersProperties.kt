package com.aechak.infra.social

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * provider별 id_token 검증 설정(auth.social.providers.*).
 * audiences는 우리 앱 키 화이트리스트 — 타 앱 발급 id_token 로그인(token substitution) 차단.
 */
@ConfigurationProperties("auth.social")
data class SocialProvidersProperties(
    val providers: Map<String, ProviderProperties> = emptyMap(),
) {
    data class ProviderProperties(
        val jwksUri: String,
        val issuer: String,
        /** 우리 앱 키 화이트리스트 — 채널별 키(네이티브 앱 키·REST/JS 키·Services ID)를 함께 등록한다. */
        val audiences: List<String> = emptyList(),
        /** 웹 채널 code 교환용 token 엔드포인트 — 비면 해당 provider의 code 채널 비활성. */
        val tokenUri: String = "",
        /** 서버 콜백 플로우의 authorize(로그인 화면) 엔드포인트 — 비면 웹 로그인 진입 비활성. */
        val authorizeUri: String = "",
        /** code 교환·authorize에 쓰는 client_id(카카오=REST API 키) — 비면 code 채널 비활성. */
        val clientId: String = "",
    )
}
