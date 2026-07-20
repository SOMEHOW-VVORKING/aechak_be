package com.aechak.infra.s3

import com.aechak.application.file.error.FileErrorCode
import com.aechak.application.file.port.FileStorage
import com.aechak.application.file.port.IssueFileUrl
import com.aechak.application.file.port.enums.FileType
import com.aechak.application.file.port.enums.StorageCategory
import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.common.error.BusinessException
import com.aechak.domain.support.Ulid
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest

@Component
class FileStorageAdapter(
    private val s3Properties: S3Properties,
    private val s3Client: S3Client,
    private val s3Presigner: S3Presigner,
) : FileStorage {
    override fun issueUploadUrl(
        purpose: UploadPurpose,
        fileType: FileType,
        userId: Long,
    ): IssueFileUrl {
        val key = tmpKeyOf(userId, purpose, fileType)
        val bucket = bucketOf(purpose.category)

        val presigned =
            s3Presigner.presignPutObject { presigned: PutObjectPresignRequest.Builder ->
                presigned.signatureDuration(s3Properties.presignTtl).putObjectRequest { request ->
                    request
                        .bucket(bucket)
                        .contentType(fileType.mimeType)
                        .key(key)
                        .build()
                }
            }

        return IssueFileUrl(presigned.url().toString(), key)
    }

    private fun bucketOf(category: StorageCategory): String =
        when (category) {
            StorageCategory.MEDIA -> s3Properties.mediaBucket
            StorageCategory.DOCS -> s3Properties.docsBucket
        }

    private fun tmpKeyOf(
        userId: Long,
        purpose: UploadPurpose,
        fileType: FileType,
    ): String = "$TMP_PREFIX/$userId/${purpose.prefix}/${Ulid.generate()}.${fileType.extension}"

    private fun tmpPrefixOf(userId: Long): String = "$TMP_PREFIX/$userId/"

    override fun promote(
        tmpKey: String,
        userId: Long,
        purpose: UploadPurpose,
    ): String {
        if (!tmpKey.startsWith(tmpPrefixOf(userId))) {
            throw BusinessException(FileErrorCode.FILE_ACCESS_DENIED)
        }

        val bucket = bucketOf(purpose.category)
        val promotedKey = "${purpose.prefix}/${tmpKey.substringAfterLast('/')}"

        try {
            s3Client.copyObject { copy ->
                copy
                    .sourceBucket(bucket)
                    .sourceKey(tmpKey)
                    .destinationBucket(bucket)
                    .destinationKey(promotedKey)
            }
        } catch (e: NoSuchKeyException) {
            throw BusinessException(FileErrorCode.FILE_NOT_FOUND, e)
        }

        return promotedKey
    }

    companion object {
        private const val TMP_PREFIX = "tmp"
    }
}
