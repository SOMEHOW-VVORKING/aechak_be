package com.aechak.application.file.service

import com.aechak.application.file.error.FileErrorCode
import com.aechak.application.file.port.FileKey
import com.aechak.application.file.port.FileStorage
import com.aechak.application.file.port.IssueFileUrl
import com.aechak.application.file.port.enums.FileType
import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.application.file.usecase.command.IssuePresignedUrlCommand
import com.aechak.application.file.usecase.command.PromoteFileCommand
import com.aechak.common.error.BusinessException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val PROMOTED_SENTINEL = "promoted/sentinel-key"

/**
 * 계약 — 발급·승격의 보안 경계. 깨지면 남이 발급받은 키나 다른 용도의 키가 S3에 닿는다.
 */
class FileServiceTest {
    private val service = FileService(FakeFileStorage())

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

    private class FakeFileStorage : FileStorage {
        override fun issueUploadUrl(
            purpose: UploadPurpose,
            fileType: FileType,
            userId: Long,
        ): IssueFileUrl =
            IssueFileUrl(
                url = "https://fake-presigned",
                key = "tmp/$userId/${purpose.prefix}/ULID.${fileType.extension}",
            )

        override fun promote(
            tmpKey: String,
            purpose: UploadPurpose,
        ): String = PROMOTED_SENTINEL

        override fun publicUrlOf(key: String): String = "https://fake-cdn/$key"
    }

    companion object {
        private const val USER_ID = 1L
    }
}
