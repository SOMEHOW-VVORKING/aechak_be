package com.aechak.api.support

import com.aechak.application.file.port.FileKey
import com.aechak.application.file.port.FileStorage
import com.aechak.application.file.port.IssueFileUrl
import com.aechak.application.file.port.enums.FileType
import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.domain.support.Ulid

/**
 * 실 S3 대신 키 규칙만 흉내냄.
 * promotedKeys는 거절된 요청이 스토리지에 복사본을 남기지 않는지 보려고 기록함.
 */
class FakeFileStorage : FileStorage {
    private val promoted = mutableListOf<String>()

    val promotedKeys: List<String> get() = promoted.toList()

    fun clearPromoted() = promoted.clear()

    override fun issueUploadUrl(
        purpose: UploadPurpose,
        fileType: FileType,
        userId: Long,
    ): IssueFileUrl {
        val key = "${FileKey.tmpPrefixOf(userId, purpose)}${Ulid.generate()}.${fileType.extension}"
        return IssueFileUrl("https://fake-presigned.local/$key", key)
    }

    override fun promote(
        tmpKey: String,
        purpose: UploadPurpose,
    ): String {
        val key = "${purpose.prefix}/${tmpKey.substringAfterLast('/')}"
        promoted += key
        return key
    }

    override fun publicUrlOf(key: String): String = "https://fake-cdn.local/$key"
}
