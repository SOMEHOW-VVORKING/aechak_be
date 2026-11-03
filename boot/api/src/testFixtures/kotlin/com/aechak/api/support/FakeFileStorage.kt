package com.aechak.api.support

import com.aechak.application.file.error.FileErrorCode
import com.aechak.application.file.port.FileKey
import com.aechak.application.file.port.FileStorage
import com.aechak.application.file.port.IssueFileUrl
import com.aechak.application.file.port.enums.FileType
import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.common.error.BusinessException
import com.aechak.domain.support.Ulid
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * S3를 호출하지 않고 운영 코드와 같은 키 형식으로 동작한다.
 * promotedKeys와 deletedKeys로 테스트 중 실행된 스토리지 작업을 확인할 수 있다.
 * 컨텍스트가 공유하는 빈이라 이 fake를 쓰는 테스트 클래스는 @BeforeEach에서 reset()을 부른다.
 */
class FakeFileStorage : FileStorage {
    // 동시 승격과 동시 삭제에서도 기록을 잃지 않게 한다
    private val promoted = CopyOnWriteArrayList<String>()
    private val deleted = CopyOnWriteArrayList<String>()
    private val failingDeleteKeys = ConcurrentHashMap.newKeySet<String>()

    val promotedKeys: List<String> get() = promoted.toList()

    /** 테스트 중 삭제된 스토리지 키. */
    val deletedKeys: List<String> get() = deleted.toList()

    fun clearPromoted() = promoted.clear()

    /** 승격 기록, 삭제 기록, 삭제 실패 키를 모두 비운다. */
    fun reset() {
        promoted.clear()
        deleted.clear()
        failingDeleteKeys.clear()
    }

    /** 이 키들의 delete가 예외를 던지게 한다. 빈 집합을 넘기면 모든 삭제가 다시 성공한다. */
    fun failDeletesOf(keys: Set<String>) {
        failingDeleteKeys.clear()
        failingDeleteKeys.addAll(keys)
    }

    override fun issueUploadUrl(
        purpose: UploadPurpose,
        fileType: FileType,
        userId: Long,
        contentLength: Long?,
    ): IssueFileUrl {
        val key = "${FileKey.tmpPrefixOf(userId, purpose)}${Ulid.generate()}.${fileType.extension}"
        return IssueFileUrl("https://fake-presigned.local/$key", key)
    }

    override fun promote(
        tmpKey: String,
        purpose: UploadPurpose,
    ): String {
        // 운영에서도 지운 tmp 키를 복사하면 S3가 NoSuchKey를 주고 어댑터가 100001로 바꾼다
        if (tmpKey in deleted) throw BusinessException(FileErrorCode.FILE_NOT_FOUND)
        val key = "${purpose.prefix}/${tmpKey.substringAfterLast('/')}"
        promoted += key
        return key
    }

    override fun publicUrlOf(key: String): String = "https://fake-cdn.local/$key"

    override fun issueDownloadUrl(
        key: String,
        purpose: UploadPurpose,
    ): String = "https://fake-download.local/$key"

    override fun delete(
        key: String,
        purpose: UploadPurpose,
    ) {
        if (key in failingDeleteKeys) throw IllegalStateException("삭제 실패로 지정한 키: $key")
        deleted += key
    }
}
