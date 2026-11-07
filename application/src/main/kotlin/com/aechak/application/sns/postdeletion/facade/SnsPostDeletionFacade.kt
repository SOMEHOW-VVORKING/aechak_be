package com.aechak.application.sns.postdeletion.facade

import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.application.file.usecase.FileUseCase
import com.aechak.application.file.usecase.command.DeleteFileCommand
import com.aechak.application.sns.postdeletion.service.PostDeletionService
import com.aechak.application.sns.postdeletion.usecase.SnsPostDeletionUseCase
import com.aechak.domain.sns.post.enums.PostDeletionReason
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * SNS 게시물 삭제 진입점. 금지어 판정을 쓰지 않아 다른 실행 모듈이 이 패키지만 스캔할 수 있다.
 * 작성자 삭제는 미디어 파일을 커밋 뒤에 지우므로 바깥 트랜잭션 안에서 부르지 않는다.
 * 안에서 부르면 커밋 전에 파일을 지우고 바깥이 롤백되면 파일만 사라진다.
 */
@Service
class SnsPostDeletionFacade(
    private val postDeletionService: PostDeletionService,
    private val fileUseCase: FileUseCase,
    transactionManager: PlatformTransactionManager,
) : SnsPostDeletionUseCase {
    private val log = LoggerFactory.getLogger(javaClass)

    private val tx =
        TransactionTemplate(transactionManager).apply {
            isolationLevel = TransactionDefinition.ISOLATION_READ_COMMITTED
        }

    override fun deleteByAuthor(
        userId: Long,
        publicId: String,
    ) {
        val mediaKeys =
            tx.execute {
                postDeletionService.delete(postDeletionService.lockOwnedBy(publicId, userId), PostDeletionReason.AUTHOR)
            }!!
        deleteMediaFiles(publicId, mediaKeys)
    }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun deleteByOperator(publicId: String) {
        val mediaKeys = postDeletionService.delete(postDeletionService.lock(publicId), PostDeletionReason.OPERATOR)
        deleteMediaFilesAfterCommit(publicId, mediaKeys)
    }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun deleteByWithdrawal(authorId: Long) {
        postDeletionService.lockPublishedOf(authorId).forEach { post ->
            deleteMediaFilesAfterCommit(post.publicId, postDeletionService.delete(post, PostDeletionReason.WITHDRAWAL))
        }
    }

    private fun deleteMediaFilesAfterCommit(
        publicId: String,
        keys: List<String>,
    ) {
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() = deleteMediaFiles(publicId, keys)
            },
        )
    }

    // shortcut: S3 삭제가 실패하면 지운 게시물의 파일이 공개 주소에 남는다. 실패 로그가 한 번이라도 보이면
    // status = DELETED 게시물마다 이 UseCase를 다시 부르는 청소 작업을 boot/batch에 추가한다
    private fun deleteMediaFiles(
        publicId: String,
        keys: List<String>,
    ) {
        keys.forEach { key ->
            runCatching { fileUseCase.delete(DeleteFileCommand(key, UploadPurpose.SNS_POST_IMAGE)) }
                .onFailure { log.error("게시물 삭제 후 파일 삭제 실패, 수동 확인 필요 (publicId={}, key={})", publicId, key, it) }
        }
    }
}
