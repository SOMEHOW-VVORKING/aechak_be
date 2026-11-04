package com.aechak.application.moderation.port

/** 글에서 금칙어를 찾아 마스킹 결과와 매치 비율을 제공 */
fun interface ProfanityScanner {
    fun scan(content: String): ProfanityScanResult
}
