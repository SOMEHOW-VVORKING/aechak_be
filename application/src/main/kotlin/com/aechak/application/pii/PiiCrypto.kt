package com.aechak.application.pii

/**
 * 민감정보(PII) 암호화 포트 — 저장용 암호문(AES, 복원 가능)과 검색용 해시(HMAC, 결정적·역산 불가)를 만든다.
 * 구현은 영속 모듈이 소유하고 boot가 조립한다.
 * hmac의 context는 용도 라벨("phone" 등) — 입력 결합 형식은 저장된 전 행과 공유하는 영구 계약이라 바꾸면 인덱스가 고아가 된다.
 */
interface PiiCrypto {
    fun encrypt(plain: String): ByteArray

    fun decrypt(cipher: ByteArray): String

    fun hmac(
        context: String,
        value: String,
    ): ByteArray
}
