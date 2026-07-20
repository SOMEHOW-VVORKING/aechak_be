package com.aechak.application.file.service

import com.aechak.application.file.error.FileErrorCode
import com.aechak.application.file.port.FileStorage
import com.aechak.application.file.port.IssueFileUrl
import com.aechak.application.file.port.enums.FileType
import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.application.file.usecase.command.IssuePresignedUrlCommand
import com.aechak.common.error.BusinessException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 발급 화이트리스트 계약 — 허용되지 않은 MIME은 포트에 닿기 전에 거절되고, 허용된 것만 위임된다. */
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
        // PDF는 유효한 FileType이지만 USER_PROFILE 허용 목록(png·jpeg·webp) 밖이다
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

    /** 외부 경계(S3)만 가짜로 — 발급 로직은 진짜 FileService가 돈다. */
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
            userId: Long,
            purpose: UploadPurpose,
        ): String = tmpKey
    }

    companion object {
        private const val USER_ID = 1L
    }
}
