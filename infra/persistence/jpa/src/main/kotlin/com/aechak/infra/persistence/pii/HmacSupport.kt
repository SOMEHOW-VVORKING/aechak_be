package com.aechak.infra.persistence.pii

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HMAC-SHA256 검색용 해시(blind index). 같은 입력이면 항상 같은 32바이트 — 동등 검색·UNIQUE의 재료다.
 * 입력 결합 "context:value"는 저장된 전 행과 공유하는 영구 계약 — 형식·라벨을 바꾸면 기존 인덱스가 전부 고아가 된다.
 * 키는 평문 등가 시크릿(입력 공간이 좁아 키·DB 동시 유출 시 전수 역산 가능) — AES 키와 분리 보관한다.
 */
class HmacSupport(
    private val key: ByteArray,
) {
    fun hmac(
        context: String,
        value: String,
    ): ByteArray =
        Mac
            .getInstance(ALGORITHM)
            .apply { init(SecretKeySpec(key, ALGORITHM)) }
            .doFinal("$context:$value".toByteArray(Charsets.UTF_8))

    companion object {
        private const val ALGORITHM = "HmacSHA256"
    }
}
