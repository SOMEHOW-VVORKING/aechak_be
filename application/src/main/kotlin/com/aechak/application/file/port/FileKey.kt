package com.aechak.application.file.port

import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.domain.support.Ulid

/**
 * 파일 업로드 키 네이밍 규약
 */
object FileKey {
    const val TMP_PREFIX = "tmp"

    /** 이 유저가 발급받은 tmp 키의 공통 접두 — 소유검증(startsWith)에 씀 */
    fun tmpOwnerPrefix(userId: Long): String = "$TMP_PREFIX/$userId/"

    /** 이 유저가 이 용도로 발급받은 tmp 키의 접두 — 용도검증(startsWith)에 씀 */
    fun tmpPrefixOf(
        userId: Long,
        purpose: UploadPurpose,
    ): String = "${tmpOwnerPrefix(userId)}${purpose.prefix}/"

    /** tmp 키를 승격할 영구 키. 보통은 tmp 파일 이름을 그대로 쓰고 newKeyOnPromote 용도는 부를 때마다 새 이름을 만든다 */
    fun promotedKeyOf(
        tmpKey: String,
        purpose: UploadPurpose,
    ): String =
        if (purpose.newKeyOnPromote) {
            "${purpose.prefix}/${Ulid.generate()}.${tmpKey.substringAfterLast('.')}"
        } else {
            "${purpose.prefix}/${tmpKey.substringAfterLast('/')}"
        }
}
