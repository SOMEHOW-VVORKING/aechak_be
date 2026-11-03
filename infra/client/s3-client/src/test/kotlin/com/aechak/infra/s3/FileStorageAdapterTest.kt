package com.aechak.infra.s3

import com.aechak.application.file.port.enums.FileType
import com.aechak.application.file.port.enums.UploadPurpose
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI
import java.net.URLDecoder
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 업로드 URL 서명에 길이가 들어가는지. presign은 네트워크 없는 로컬 연산이라 실제 presigner로 서명한다. */
class FileStorageAdapterTest {
    private val credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create("test-access-key", "test-secret-key"))
    private val adapter =
        FileStorageAdapter(
            S3Properties(mediaBucket = "test-media", docsBucket = "test-docs"),
            S3Client
                .builder()
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(credentials)
                .build(),
            S3Presigner
                .builder()
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(credentials)
                .build(),
        )

    @Test
    fun `길이를 받으면 content-length를 서명한다`() {
        val issued = adapter.issueUploadUrl(UploadPurpose.SNS_POST_IMAGE, FileType.JPEG, USER_ID, 1_843_200)

        assertTrue("content-length" in signedHeadersOf(issued.url), "길이가 다른 PUT을 S3가 거부하려면 길이가 서명에 들어가야 한다")
    }

    @Test
    fun `길이가 없으면 content-length를 서명하지 않는다`() {
        val issued = adapter.issueUploadUrl(UploadPurpose.REVIEW_IMAGE, FileType.PNG, USER_ID, null)

        assertFalse("content-length" in signedHeadersOf(issued.url), "기존 용도의 서명은 길이 없이 그대로여야 한다")
    }

    private fun signedHeadersOf(url: String): List<String> =
        URI(url)
            .rawQuery
            .split('&')
            .first { it.startsWith("X-Amz-SignedHeaders=") }
            .substringAfter('=')
            .let { URLDecoder.decode(it, Charsets.UTF_8) }
            .split(';')

    companion object {
        private const val USER_ID = 42L
    }
}
