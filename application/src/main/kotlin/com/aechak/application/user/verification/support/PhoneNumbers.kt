package com.aechak.application.user.verification.support

/**
 * 전화번호 정규화 — 이 규칙은 HMAC 입력·Redis 키와 공유되는 영구 계약이다.
 * 바꾸면 저장된 전 행의 blind index가 고아가 되므로 변경은 전량 재계산 이벤트로만 가능하다.
 */
object PhoneNumbers {
    private val FORMAT = Regex("^01[016789]\\d{7,8}$")

    /** 비숫자 제거 후 형식 방어선 — 웹 경계(@Pattern)를 통과한 입력만 도달하므로 위반은 서버 버그(500)다. */
    fun normalize(raw: String): String {
        val digits = raw.filter(Char::isDigit)
        require(FORMAT.matches(digits)) { "전화번호 형식 위반 — 웹 경계 검증과 불일치(길이=${digits.length})" }
        return digits
    }
}
