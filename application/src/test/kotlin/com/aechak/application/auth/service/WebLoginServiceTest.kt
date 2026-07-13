package com.aechak.application.auth.service

import com.aechak.application.auth.port.AuthorizationCodeExchanger
import com.aechak.application.auth.port.LoginStateStore
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.user.social.enums.SocialProvider
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 서버 콜백 로그인의 앞단 로직 검증 — 화이트리스트 정확 일치·state 1회성·콜백 URI 일관성. */
class WebLoginServiceTest {

    private val stateStore = FakeLoginStateStore()
    private val exchanger = FakeExchanger()
    private val service = WebLoginService(
        loginStateStore = stateStore,
        authorizationCodeExchanger = exchanger,
        policy = WebLoginPolicy(
            allowedReturnUrls = setOf(RETURN_URL),
            callbackBaseUrl = "http://localhost:8080/api/v1/auth/callback",
            stateTtl = Duration.ofMinutes(5),
        ),
    )

    @Test
    fun `화이트리스트에 없는 return은 400으로 거부된다 - 오픈 리다이렉트 방지`() {
        val exception = assertFailsWith<BusinessException> {
            service.prepareLogin(SocialProvider.KAKAO, "https://evil-aechak.com/login")
        }
        assertEquals(CommonErrorCode.INVALID_REQUEST, exception.errorCode)
    }

    @Test
    fun `유사 URL도 정확 일치가 아니면 거부된다`() {
        assertFailsWith<BusinessException> {
            service.prepareLogin(SocialProvider.KAKAO, "$RETURN_URL/extra") // prefix 우회 시도
        }
    }

    @Test
    fun `진입 준비 - state가 저장되고 authorize URL에 state와 서버 콜백이 실린다`() {
        val authorizeUrl = service.prepareLogin(SocialProvider.KAKAO, RETURN_URL)

        val state = exchanger.lastState
        assertTrue(authorizeUrl.contains(state!!))
        assertEquals("http://localhost:8080/api/v1/auth/callback/kakao", exchanger.lastRedirectUri)
        assertEquals(RETURN_URL, stateStore.consume(state)) // 저장 확인 겸 소비
    }

    @Test
    fun `state는 1회용이다 - 두 번째 소비는 null`() {
        service.prepareLogin(SocialProvider.KAKAO, RETURN_URL)
        val state = exchanger.lastState!!

        assertEquals(RETURN_URL, service.consumeState(state))
        assertNull(service.consumeState(state)) // 재사용(공격 신호) → 거부 근거
    }

    @Test
    fun `state는 매 진입마다 달라진다 - 추측 불가 전제`() {
        service.prepareLogin(SocialProvider.KAKAO, RETURN_URL)
        val first = exchanger.lastState
        service.prepareLogin(SocialProvider.KAKAO, RETURN_URL)
        val second = exchanger.lastState

        assertNotEquals(first, second)
        assertTrue(first!!.length >= 32)
    }

    @Test
    fun `code 교환도 authorize와 동일한 콜백 URI를 쓴다 - provider가 대조하므로`() {
        service.exchangeCode(SocialProvider.KAKAO, "abc")

        assertEquals("http://localhost:8080/api/v1/auth/callback/kakao", exchanger.lastRedirectUri)
    }

    companion object {
        private const val RETURN_URL = "http://localhost:5173/login/complete"
    }
}

private class FakeLoginStateStore : LoginStateStore {
    private val entries = mutableMapOf<String, String>()
    override fun save(state: String, returnUrl: String, ttl: Duration) {
        entries[state] = returnUrl
    }
    override fun consume(state: String): String? = entries.remove(state)
}

private class FakeExchanger : AuthorizationCodeExchanger {
    var lastState: String? = null
    var lastRedirectUri: String? = null

    override fun exchange(provider: SocialProvider, code: String, redirectUri: String): String {
        lastRedirectUri = redirectUri
        return "id-token-for-$code"
    }

    override fun buildAuthorizeUrl(provider: SocialProvider, state: String, redirectUri: String): String {
        lastState = state
        lastRedirectUri = redirectUri
        return "https://kauth.example/authorize?state=$state&redirect_uri=$redirectUri"
    }
}
