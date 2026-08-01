package com.aechak.application.file.port

import com.aechak.application.file.port.enums.UploadPurpose

/**
 * 파일 업로드 키 네이밍 규약
 */
object FileKey {
    const val TMP_PREFIX = "tmp"

    fun isTmp(key: String): Boolean = key.startsWith("$TMP_PREFIX/")

    /** 이 유저가 발급받은 tmp 키의 공통 접두 — 소유검증(startsWith)에 씀 */
    fun tmpOwnerPrefix(userId: Long): String = "$TMP_PREFIX/$userId/"

    /** 이 유저가 이 용도로 발급받은 tmp 키의 접두 — 용도검증(startsWith)에 씀 */
    fun tmpPrefixOf(
        userId: Long,
        purpose: UploadPurpose,
    ): String = "${tmpOwnerPrefix(userId)}${purpose.prefix}/"
}
