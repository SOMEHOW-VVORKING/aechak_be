package com.aechak.application.file.service

import com.aechak.application.file.error.FileErrorCode
import com.aechak.application.file.port.FileKey
import com.aechak.application.file.port.FileStorage
import com.aechak.application.file.port.IssueFileUrl
import com.aechak.application.file.port.enums.FileType
import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.application.file.usecase.command.DeleteFileCommand
import com.aechak.application.file.usecase.command.IssuePresignedUrlCommand
import com.aechak.application.file.usecase.command.PromoteFileCommand
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val PROMOTED_SENTINEL = "promoted/sentinel-key"

/**
 * 다른 사용자의 키나 다른 용도로 발급된 키가 스토리지에 전달되지 않는지 검증한다.
 */
class FileServiceTest {
    private val storage = FakeFileStorage()
    private val service = FileService(storage)

    @Test
    fun `화이트리스트에 없는 MIME 타입이면 UNSUPPORTED_FILE_TYPE로 거절한다`() {
        val command = IssuePresignedUrlCommand(UploadPurpose.USER_PROFILE, "image/gif", USER_ID)

        val ex = assertFailsWith<BusinessException> { service.issuePresignedUrl(command) }

        assertEquals(FileErrorCode.UNSUPPORTED_FILE_TYPE, ex.errorCode, "정의되지 않은 타입은 거절해야 한다")
    }

    @Test
    fun `purpose가 허용하지 않는 타입이면 거절한다`() {
        // PDF는 유효한 FileType이지만 USER_PROFILE 허용 목록 밖
        val command = IssuePresignedUrlCommand(UploadPurpose.USER_PROFILE, "application/pdf", USER_ID)

        val ex = assertFailsWith<BusinessException> { service.issuePresignedUrl(command) }

        assertEquals(FileErrorCode.UNSUPPORTED_FILE_TYPE, ex.errorCode, "purpose별 허용 밖 타입은 거절해야 한다")
    }

    @Test
    fun `허용된 타입이면 포트에 위임하고 발급 결과를 반환한다`() {
        val command = IssuePresignedUrlCommand(UploadPurpose.USER_PROFILE, "image/png", USER_ID)

        val result = service.issuePresignedUrl(command)

        assertEquals("https://fake-presigned", result.url, "포트 발급 URL이 그대로 전달돼야 한다")
        assertTrue(result.key.endsWith(".png"), "검증 통과한 MIME의 확장자(png)가 키에 반영돼야 한다")
    }

    @Test
    fun `본인 소유가 아닌 tmp 키면 FILE_ACCESS_DENIED로 거절한다`() {
        val command = PromoteFileCommand("tmp/999/users/profile/x.png", USER_ID, UploadPurpose.USER_PROFILE)

        val ex = assertFailsWith<BusinessException> { service.promote(command) }

        assertEquals(FileErrorCode.FILE_ACCESS_DENIED, ex.errorCode, "다른 유저의 tmp 키는 승격할 수 없어야 한다")
    }

    @Test
    fun `userId prefix가 부분일치해도 다른 유저 키면 거절한다`() {
        // 접두 검증에 후행 슬래시가 없으면 id=1이 id=12의 파일을 씀
        val command = PromoteFileCommand("tmp/12/users/profile/x.png", USER_ID, UploadPurpose.USER_PROFILE)

        val ex = assertFailsWith<BusinessException> { service.promote(command) }

        assertEquals(FileErrorCode.FILE_ACCESS_DENIED, ex.errorCode, "tmp/12는 userId=1 소유가 아니어야 한다")
    }

    @Test
    fun `tmp 스테이징 키가 아니면 거절한다`() {
        val command = PromoteFileCommand("users/profile/x.png", USER_ID, UploadPurpose.USER_PROFILE)

        val ex = assertFailsWith<BusinessException> { service.promote(command) }

        assertEquals(FileErrorCode.FILE_ACCESS_DENIED, ex.errorCode, "tmp staging 밖 키는 승격 대상이 아니어야 한다")
    }

    @Test
    fun `발급 용도와 다른 경로의 tmp 키면 FILE_PURPOSE_MISMATCH로 거절한다`() {
        val command = PromoteFileCommand("tmp/$USER_ID/other/x.png", USER_ID, UploadPurpose.USER_PROFILE)

        val ex = assertFailsWith<BusinessException> { service.promote(command) }

        assertEquals(FileErrorCode.FILE_PURPOSE_MISMATCH, ex.errorCode, "다른 용도로 발급된 키는 소유 거절과 구분해 거절해야 한다")
    }

    @Test
    fun `본인 tmp 키면 소유검증 통과 후 storage 승격 결과를 그대로 반환한다`() {
        val command =
            PromoteFileCommand("${FileKey.tmpPrefixOf(USER_ID, UploadPurpose.USER_PROFILE)}ULID.png", USER_ID, UploadPurpose.USER_PROFILE)

        val result = service.promote(command)

        assertEquals(PROMOTED_SENTINEL, result.key, "가드 통과 후 storage가 돌려준 키를 그대로 위임해야 한다")
    }

    @Test
    fun `삭제는 키를 그대로 스토리지에 넘긴다`() {
        val key = "users/profile/ULID.png"

        service.delete(DeleteFileCommand(key, UploadPurpose.USER_PROFILE))

        // DB에 저장된 키를 삭제하므로 임시 키의 사용자 및 용도 검증을 거치지 않는다.
        assertEquals(listOf(key), storage.deletedKeys, "삭제는 스토리지 삭제로 그대로 위임해야 한다")
    }

    @Test
    fun `상한이 있는 용도에 길이가 없으면 INVALID_REQUEST로 거절한다`() {
        val command = IssuePresignedUrlCommand(UploadPurpose.SNS_POST_IMAGE, "image/jpeg", USER_ID)

        val ex = assertFailsWith<BusinessException> { service.issuePresignedUrl(command) }

        assertEquals(CommonErrorCode.INVALID_REQUEST, ex.errorCode, "상한이 있는 용도는 서명에 넣을 길이를 받아야 한다")
    }

    @Test
    fun `길이가 상한을 넘으면 FILE_TOO_LARGE로 거절한다`() {
        val command = IssuePresignedUrlCommand(UploadPurpose.SNS_POST_IMAGE, "image/jpeg", USER_ID, SNS_POST_IMAGE_MAX_BYTES + 1)

        val ex = assertFailsWith<BusinessException> { service.issuePresignedUrl(command) }

        assertEquals(FileErrorCode.FILE_TOO_LARGE, ex.errorCode, "상한을 1바이트라도 넘으면 거절해야 한다")
    }

    @Test
    fun `형식 검증이 길이 검증보다 먼저다`() {
        val command = IssuePresignedUrlCommand(UploadPurpose.SNS_POST_IMAGE, "video/webm", USER_ID)

        val ex = assertFailsWith<BusinessException> { service.issuePresignedUrl(command) }

        assertEquals(FileErrorCode.UNSUPPORTED_FILE_TYPE, ex.errorCode, "형식과 길이가 모두 틀리면 형식 오류가 먼저다")
    }

    @Test
    fun `길이가 상한과 같으면 그 길이로 서명을 요청한다`() {
        val command = IssuePresignedUrlCommand(UploadPurpose.SNS_POST_IMAGE, "image/jpeg", USER_ID, SNS_POST_IMAGE_MAX_BYTES)

        service.issuePresignedUrl(command)

        assertEquals(listOf<Long?>(SNS_POST_IMAGE_MAX_BYTES), storage.issuedContentLengths, "상한과 같은 길이는 그대로 서명에 넣어야 한다")
    }

    @Test
    fun `상한이 없는 용도는 받은 길이를 서명에 넣지 않는다`() {
        val command = IssuePresignedUrlCommand(UploadPurpose.REVIEW_IMAGE, "image/png", USER_ID, 1024)

        service.issuePresignedUrl(command)

        assertEquals(listOf<Long?>(null), storage.issuedContentLengths, "기존 용도의 서명은 길이 없이 그대로여야 한다")
    }

    private class FakeFileStorage : FileStorage {
        val deletedKeys = mutableListOf<String>()
        val issuedContentLengths = mutableListOf<Long?>()

        override fun issueUploadUrl(
            purpose: UploadPurpose,
            fileType: FileType,
            userId: Long,
            contentLength: Long?,
        ): IssueFileUrl {
            issuedContentLengths += contentLength
            return IssueFileUrl(
                url = "https://fake-presigned",
                key = "tmp/$userId/${purpose.prefix}/ULID.${fileType.extension}",
            )
        }

        override fun promote(
            tmpKey: String,
            purpose: UploadPurpose,
        ): String = PROMOTED_SENTINEL

        override fun publicUrlOf(key: String): String = "https://fake-cdn/$key"

        override fun issueDownloadUrl(
            key: String,
            purpose: UploadPurpose,
        ): String = "https://fake-download/$key"

        override fun delete(
            key: String,
            purpose: UploadPurpose,
        ) {
            deletedKeys += key
        }
    }

    companion object {
        private const val USER_ID = 1L
        private const val SNS_POST_IMAGE_MAX_BYTES = 10_485_760L
    }
}
