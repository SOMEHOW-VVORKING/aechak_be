package com.aechak.application.user.user.support

/**
 * 표시용 전화번호 마스킹(가운데 자리 전체 — 통용 표기, 예 010-****-5678).
 * 원문은 문자 발송 외 서버 밖 반출 금지 — 응답·로그 모두 이 유틸을 거친다.
 */
object PhoneNumberMasker {
    fun mask(normalizedPhoneNumber: String): String {
        val head = normalizedPhoneNumber.take(3)
        val tail = normalizedPhoneNumber.takeLast(4)
        val masked = "*".repeat((normalizedPhoneNumber.length - head.length - tail.length).coerceAtLeast(1))
        return "$head-$masked-$tail"
    }
}
