package com.aechak.application.auth.port

/**
 * 비밀번호 해싱·대조 포트. 알고리즘을 application 밖에 두어 교체가 이 인터페이스 뒤에서 끝난다.
 *
 * domain 포트가 아닌 이유는 소비자가 정의하는 계약이기 때문이다 — 애그리거트 계약이 아니라
 * 로그인 유스케이스가 필요로 하는 부품이고, UserRoleReader·TokenCodec과 같은 결이다.
 */
interface PasswordHasher {
    fun hash(raw: String): String

    /**
     * 평문과 저장된 해시를 대조한다.
     *
     * 해시가 깨져 있거나 알고리즘을 알 수 없으면 예외를 올리지 말고 false를 돌려준다 —
     * 로그인 실패와 구분되는 응답이 나가면 그 차이가 공격자에게 신호가 된다.
     */
    fun matches(
        raw: String,
        hash: String,
    ): Boolean
}
