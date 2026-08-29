package com.aechak.infra.s3

import com.aechak.application.file.error.FileErrorCode
import com.aechak.application.file.port.FileKey
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

    override fun publicUrlOf(key: String): String = "${s3Properties.mediaPublicBaseUrl.trimEnd('/')}/${key.trimStart('/')}"

    override fun delete(
        key: String,
        purpose: UploadPurpose,
    ) {
        s3Client.deleteObject { delete ->
            delete
                .bucket(bucketOf(purpose.category))
                .key(key)
        }
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
    ): String = "${FileKey.TMP_PREFIX}/$userId/${purpose.prefix}/${Ulid.generate()}.${fileType.extension}"

    override fun promote(
        tmpKey: String,
        purpose: UploadPurpose,
    ): String {
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
}
