package com.aechak.api.support

import com.aechak.application.file.port.FileKey
import com.aechak.application.file.port.FileStorage
import com.aechak.application.file.port.IssueFileUrl
import com.aechak.application.file.port.enums.FileType
import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.domain.support.Ulid

/**
 * S3를 호출하지 않고 운영 코드와 같은 키 형식으로 동작한다.
 * promotedKeys와 deletedKeys로 테스트 중 실행된 스토리지 작업을 확인할 수 있다.
 */
class FakeFileStorage : FileStorage {
    private val promoted = mutableListOf<String>()
    private val deleted = mutableListOf<String>()

    val promotedKeys: List<String> get() = promoted.toList()

    /** 테스트 중 삭제된 스토리지 키. */
    val deletedKeys: List<String> get() = deleted.toList()

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

    override fun delete(
        key: String,
        purpose: UploadPurpose,
    ) {
        deleted += key
    }
}
