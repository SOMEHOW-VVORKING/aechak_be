package com.aechak.api.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * PII 암호화 키 바인딩. 전 필드에 기본값이 없다 — 미주입이면 부팅 실패가 정답이다(fail-fast).
 * JWT처럼 임시 키를 생성하는 폴백은 금지: PII 키 분실은 재로그인이 아니라 데이터 영구 복호 불능이다.
 */
@ConfigurationProperties("aechak.pii")
data class PiiKeyProperties(
    val activeVersion: Int,
    val aesKeys: Map<Int, String>,
    val hmacKey: String,
)
