package com.aechak.application.pii.port

/**
 * HMAC blind index의 용도 라벨 — 같은 값이라도 용도가 다르면 다른 해시가 나오게 해 검색 공간을 가른다
 * (전화 뒷4 "5678"과 계좌 뒷4 "5678"이 같은 바이트로 저장되면 무관한 행끼리 매칭된다).
 * 라벨 문자열은 저장된 전 행과 공유하는 영구 계약 — 바꾸면 기존 인덱스가 전부 고아가 된다.
 * 라벨에 결합 구분자(":")를 쓰면 경계가 흔들려 다른 용도끼리 같은 해시가 될 수 있다(HmacSupport가 거부).
 */
enum class PiiContext(
    val label: String,
) {
    PHONE("phone"),
    PHONE_LAST4("phone-last4"),
}
