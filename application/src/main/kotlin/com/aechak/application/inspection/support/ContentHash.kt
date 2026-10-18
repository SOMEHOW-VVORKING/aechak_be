package com.aechak.application.inspection.support

import java.security.MessageDigest

/**
 * 판정 대상 본문의 지문.
 *
 * 같은 내용을 두 번 판정하지 않기 위한 열쇠다. 공백 차이로 해시가 갈라지면 재과금이 나므로
 * 줄바꿈과 연속 공백을 한 칸으로 줄여 정규화한 뒤 해시한다.
 */
object ContentHash {
    private val WHITESPACE = Regex("\\s+")

    /** 조각 사이에 구분자를 넣는다. 넣지 않으면 경계가 밀린 다른 내용이 같은 해시를 받는다 */
    private const val SEPARATOR = "||"

    fun of(parts: List<String?>): String {
        val normalized =
            parts
                .filterNotNull()
                .joinToString(SEPARATOR) { WHITESPACE.replace(it.trim(), " ") }
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
