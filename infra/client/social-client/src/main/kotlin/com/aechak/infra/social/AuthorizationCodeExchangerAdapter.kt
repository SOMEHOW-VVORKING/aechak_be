package com.aechak.infra.social

import com.aechak.application.auth.error.AuthErrorCode
import com.aechak.application.auth.port.AuthorizationCodeExchanger
import com.aechak.common.error.BusinessException
import com.aechak.domain.user.social.enums.SocialProvider
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.util.UriComponentsBuilder

/**
 * AuthorizationCodeExchanger 포트 구현 — OAuth 표준 code 교환(grant_type=authorization_code).
 * provider 차이는 설정(token-uri·client-id)뿐이라 코드 경로는 공통이다.
 * 카카오 콘솔에서 client secret을 활성화하면 파라미터 추가가 필요 — 현재 미사용 가정.
 */
@Component
class AuthorizationCodeExchangerAdapter(
    private val properties: SocialProvidersProperties,
) : AuthorizationCodeExchanger {

    private val restClient = RestClient.builder()
        .requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(TIMEOUT_MS)
                setReadTimeout(TIMEOUT_MS)
            },
        )
        .build()

    override fun exchange(provider: SocialProvider, code: String, redirectUri: String): String {
        val providerProperties = properties.providers[provider.name.lowercase()]
        if (providerProperties == null || providerProperties.tokenUri.isBlank() || providerProperties.clientId.isBlank()) {
            throw BusinessException(AuthErrorCode.UNSUPPORTED_PROVIDER)
        }

        val form = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "authorization_code")
            add("client_id", providerProperties.clientId)
            add("redirect_uri", redirectUri)
            add("code", code)
            if (providerProperties.clientSecret.isNotBlank()) {
                add("client_secret", providerProperties.clientSecret) // 미동봉 시 provider가 KOE010으로 거부
            }
        }

        val body = try {
            restClient.post()
                .uri(providerProperties.tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(object : ParameterizedTypeReference<Map<String, Any>>() {})
        } catch (e: RestClientException) {
            // 만료·위조 code, redirect_uri 불일치 등 — provider가 교환을 거부한 경우
            throw BusinessException(AuthErrorCode.INVALID_SOCIAL_TOKEN, e)
        }

        return body?.get(ID_TOKEN_FIELD) as? String
            ?: throw BusinessException(AuthErrorCode.INVALID_SOCIAL_TOKEN) // openid scope 누락 등으로 id_token 부재
    }

    override fun buildAuthorizeUrl(provider: SocialProvider, state: String, redirectUri: String): String {
        val providerProperties = properties.providers[provider.name.lowercase()]
        if (providerProperties == null || providerProperties.authorizeUri.isBlank() || providerProperties.clientId.isBlank()) {
            throw BusinessException(AuthErrorCode.UNSUPPORTED_PROVIDER)
        }
        return UriComponentsBuilder.fromUriString(providerProperties.authorizeUri)
            .queryParam("client_id", providerProperties.clientId)
            .queryParam("redirect_uri", redirectUri)
            .queryParam("response_type", "code")
            .queryParam("scope", "openid")
            .queryParam("state", state)
            .build()
            .toUriString()
    }

    companion object {
        private const val TIMEOUT_MS = 3_000
        private const val ID_TOKEN_FIELD = "id_token"
    }
}
