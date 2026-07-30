package com.aechak.api.support

import com.aechak.application.file.port.FileKey
import com.aechak.application.file.port.FileStorage
import com.aechak.application.file.port.IssueFileUrl
import com.aechak.application.file.port.enums.FileType
import com.aechak.application.file.port.enums.UploadPurpose

/**
 * S3 어댑터 대역 — 통합 테스트에서 외부 호출 없이 승격 흐름을 검증한다.
 * 승격 호출을 기록해 "호출됨/스킵"을 단언할 수 있게 하고, 키 변환 규칙은 실제 어댑터와 동일하게 유지한다.
 */
class FakeFileStorage : FileStorage {
    val promotedTmpKeys = mutableListOf<String>()

    override fun issueUploadUrl(
        purpose: UploadPurpose,
        fileType: FileType,
        userId: Long,
    ): IssueFileUrl {
        val key = "${FileKey.tmpPrefixOf(userId, purpose)}fake.${fileType.extension}"
        return IssueFileUrl(url = "https://fake.upload/$key", key = key)
    }

    override fun promote(
        tmpKey: String,
        purpose: UploadPurpose,
    ): String {
        promotedTmpKeys += tmpKey
        return "${purpose.prefix}/${tmpKey.substringAfterLast('/')}"
    }

    override fun publicUrlOf(key: String): String = "https://cdn.test/$key"

    fun reset() = promotedTmpKeys.clear()
}
