package com.aechak.application.file.port

/**
 * 파일 업로드 키 네이밍 규약
 */
object FileKey {
    const val TMP_PREFIX = "tmp"

    /** 이 유저가 발급받은 tmp 키의 공통 접두 — 소유검증(startsWith)에 쓴다. */
    fun tmpOwnerPrefix(userId: Long): String = "$TMP_PREFIX/$userId/"
}
