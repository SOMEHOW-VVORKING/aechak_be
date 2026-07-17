package com.aechak.application.auth.service

import com.aechak.application.auth.error.AuthErrorCode
import com.aechak.application.auth.port.AuthorizationCodeExchanger
import com.aechak.application.auth.port.LoginStateStore
import com.aechak.common.error.BusinessException
import com.aechak.domain.user.social.enums.SocialProvider
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.util.Base64

/**
 * 웹(서버 콜백) 로그인의 순수 로직 보관함: returnUrl 검증·state 발급/소비·code 교환.
 * 로그인 자체(검증→가입→토큰)는 기존 파이프라인을 Facade가 조합한다 — 여기서는 그 앞단만.
 */
@Service
class WebLoginService(
    private val loginStateStore: LoginStateStore,
    private val authorizationCodeExchanger: AuthorizationCodeExchanger,
    private val policy: WebLoginPolicy,
) {

    private val random = SecureRandom()

    /** returnUrl은 화이트리스트 **정확 일치**만 통과 — 실패는 400(신뢰 불가 주소로 리다이렉트 금지). */
    fun prepareLogin(provider: SocialProvider, returnUrl: String): String {
        if (returnUrl !in policy.allowedReturnUrls) {
            throw BusinessException(AuthErrorCode.DISALLOWED_RETURN_URL)
        }
        val state = generateState()
        loginStateStore.save(state, returnUrl, policy.stateTtl)
        return authorizationCodeExchanger.buildAuthorizeUrl(provider, state, callbackUrl(provider))
    }

    /** state 1회 소비 — null이면 만료·재사용·위조(returnUrl을 모르므로 호출부는 직접 400 처리). */
    fun consumeState(state: String): String? = loginStateStore.consume(state)

    fun exchangeCode(provider: SocialProvider, code: String): String =
        authorizationCodeExchanger.exchange(provider, code, callbackUrl(provider))

    /** authorize 요청과 code 교환의 redirect_uri는 반드시 동일해야 한다(provider가 대조함). */
    private fun callbackUrl(provider: SocialProvider): String =
        "${policy.callbackBaseUrl}/${provider.name.lowercase()}"

    /** 추측 불가능해야 하므로 SecureRandom 256bit — 시간순 정렬 같은 성질이 오히려 불필요해 ULID 미사용. */
    private fun generateState(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
