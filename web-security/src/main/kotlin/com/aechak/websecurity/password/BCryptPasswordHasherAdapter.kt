package com.aechak.websecurity.password

import com.aechak.application.auth.port.PasswordHasher
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component

/**
 * PasswordHasher 포트의 BCrypt 어댑터. 토큰 코덱과 같은 결로 web-security가 소유한다 —
 * 어느 실행 모듈에서든 같은 해시를 같게 대조해야 한다.
 *
 * 강도는 기본값(10)을 쓴다. 올리면 대조 시간이 지수로 늘어 로그인 응답과 서버 CPU에 그대로 실린다.
 */
@Component
class BCryptPasswordHasherAdapter : PasswordHasher {
    private val encoder = BCryptPasswordEncoder()

    // encode 의 선언 반환형이 nullable 이라 받아 준다. BCrypt 가 null 을 돌려주는 경로는 없고,
    // 만약 돌려주면 해시 없는 자격이 저장되는 쪽이 더 위험하므로 그 자리에서 끊는다.
    override fun hash(raw: String): String = requireNotNull(encoder.encode(raw)) { "비밀번호 해싱이 null 을 돌려줬다" }

    /**
     * 저장된 해시가 BCrypt 형식이 아니면 encoder가 경고만 남기고 false를 돌려준다.
     * 예외로 바꾸지 않는다 — 깨진 해시와 틀린 비밀번호가 다른 응답을 내면 그 차이가 신호가 된다.
     */
    override fun matches(
        raw: String,
        hash: String,
    ): Boolean = encoder.matches(raw, hash)
}
