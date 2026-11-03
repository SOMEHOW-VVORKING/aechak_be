package com.aechak.application.sns.facade

import com.aechak.application.file.error.FileErrorCode
import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.application.file.usecase.FileUseCase
import com.aechak.application.file.usecase.command.DeleteFileCommand
import com.aechak.application.file.usecase.command.PromoteFileCommand
import com.aechak.application.moderation.usecase.ModerationUseCase
import com.aechak.application.moderation.usecase.result.ModerationDecision
import com.aechak.application.sns.service.PostAccessService
import com.aechak.application.sns.service.PostCommandService
import com.aechak.application.sns.usecase.SnsPostUseCase
import com.aechak.application.sns.usecase.command.CreatePostCommand
import com.aechak.application.sns.usecase.result.CreatePostResult
import com.aechak.application.sns.usecase.result.SnsPostDetailResult
import com.aechak.application.user.pet.usecase.PetProfileUseCase
import com.aechak.application.user.user.usecase.UserUseCase
import com.aechak.common.error.BusinessException
import com.aechak.domain.sns.error.SnsErrorCode
import com.aechak.domain.sns.post.Post
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

/**
 * SNS 게시물 진입점. 금지어 판정은 게시물만 쓰므로 관계 쪽 SnsFacade와 나눠 둔다.
 * 승격과 tmp 키 삭제는 트랜잭션 밖에서 한다.
 */
@Service
class SnsPostFacade(
    private val postCommandService: PostCommandService,
    private val postAccessService: PostAccessService,
    private val moderationUseCase: ModerationUseCase,
    private val userUseCase: UserUseCase,
    private val petProfileUseCase: PetProfileUseCase,
    private val fileUseCase: FileUseCase,
    transactionManager: PlatformTransactionManager,
) : SnsPostUseCase {
    private val log = LoggerFactory.getLogger(javaClass)

    private val tx =
        TransactionTemplate(transactionManager).apply {
            isolationLevel = TransactionDefinition.ISOLATION_READ_COMMITTED
        }

    override fun createPost(command: CreatePostCommand): CreatePostResult {
        findIdempotent(command)?.let { return it }

        val caption = Post.validateDraft(command.caption, command.photoKeys)?.let(::moderate)
        val petId =
            petProfileUseCase
                .getPets(command.userId)
                .pets
                .firstOrNull { it.isDefault }
                ?.petId
        // 게시물 사진은 승격마다 새 영구 키라 이 요청이 승격한 파일은 이 요청만 쓴다
        val photoKeys = mutableListOf<String>()
        try {
            command.photoKeys.mapTo(photoKeys) { promote(command.userId, it) }
        } catch (e: Exception) {
            deletePromoted(command.userId, photoKeys)
            // 같은 키의 늦은 동시 요청은 앞선 요청이 tmp 키를 지운 뒤라 100001을 받는다
            if (e is BusinessException && e.errorCode == FileErrorCode.FILE_NOT_FOUND) return findIdempotent(command) ?: throw e
            throw e
        }

        var committing = false
        val result =
            try {
                tx.execute {
                    postCommandService
                        .create(command.userId, caption, photoKeys, petId, command.idempotencyKey)
                        .also { committing = true }
                }!!
            } catch (e: Exception) {
                // 커밋 중에 난 예외는 저장됐는지 알 수 없어 파일을 남긴다. 같은 키로 다시 보내면 저장된 게시물을 찾는다
                if (committing) {
                    log.error("게시 커밋 결과를 알 수 없어 승격 파일을 남김, 수동 확인 필요 (userId={}, keys={})", command.userId, photoKeys, e)
                    throw e
                }
                deletePromoted(command.userId, photoKeys)
                // 동시 요청은 사전 조회를 둘 다 통과한다. (작성자, 멱등키) UNIQUE가 심판이고 진 쪽은 먼저 생긴 게시물을 돌려준다.
                // try가 tx.execute를 감싸야 함 — 안에서 잡으면 롤백 전용 마킹 때문에 커밋에서 다시 터짐
                if (e is DataIntegrityViolationException) return findIdempotent(command) ?: throw e
                throw e
            }
        deleteTmpKeys(command)
        return result
    }

    @Transactional(readOnly = true)
    override fun getPost(
        publicId: String,
        viewerId: Long?,
    ): SnsPostDetailResult {
        val post = postAccessService.requirePublished(publicId)
        val author = postAccessService.requireActiveAuthor(userUseCase.findPublicProfileById(post.authorId))
        val pair = postAccessService.requireNotBlocked(post.authorId, viewerId)
        val pet = post.petId?.let { petId -> petProfileUseCase.getPets(post.authorId).pets.firstOrNull { it.petId == petId } }
        return SnsPostDetailResult(
            publicId = post.publicId,
            author = author,
            pet = pet,
            caption = post.caption,
            hashtags = emptyList(),
            photoUrls = post.media.mapNotNull { fileUseCase.resolveMediaUrl(it.storageKey) },
            // 좋아요, 댓글, 리포스트는 아직 없어 0이다
            likeCount = 0,
            commentCount = 0,
            repostCount = 0,
            edited = post.editedAt != null,
            createdAt = post.createdAt,
            isMine = post.authorId == viewerId,
            following = pair.following,
            mutedByMe = pair.mutedByMe,
        )
    }

    private fun findIdempotent(command: CreatePostCommand): CreatePostResult? =
        postCommandService.findPublicIdByIdempotencyKey(command.userId, command.idempotencyKey)?.let(::CreatePostResult)

    // 차단이면 130405이고 마스킹이면 가린 글을 저장한다
    private fun moderate(caption: String): String =
        when (val decision = moderationUseCase.decide(caption)) {
            is ModerationDecision.Keep -> caption
            is ModerationDecision.Mask -> decision.displayContent
            is ModerationDecision.Block -> throw BusinessException(SnsErrorCode.SNS_POST_CAPTION_BLOCKED)
        }

    private fun promote(
        userId: Long,
        tmpKey: String,
    ): String = fileUseCase.promote(PromoteFileCommand(tmpKey, userId, UploadPurpose.SNS_POST_IMAGE)).key

    /** 저장하지 못한 요청이 승격한 파일을 지운다. 승격마다 새 키라 다른 게시물의 파일이 아니다 */
    private fun deletePromoted(
        userId: Long,
        keys: List<String>,
    ) {
        keys.forEach { key ->
            runCatching { fileUseCase.delete(DeleteFileCommand(key, UploadPurpose.SNS_POST_IMAGE)) }
                .onFailure { log.error("게시 실패 후 승격 파일 삭제 실패, 수동 확인 필요 (userId={}, key={})", userId, key, it) }
        }
    }

    /** 게시가 커밋된 뒤 승격에 쓴 tmp 키를 지운다. 실패해도 게시는 되돌리지 않는다 */
    private fun deleteTmpKeys(command: CreatePostCommand) {
        command.photoKeys.forEach { key ->
            runCatching { fileUseCase.delete(DeleteFileCommand(key, UploadPurpose.SNS_POST_IMAGE)) }
                .onFailure { log.error("게시 후 tmp 키 삭제 실패, 수동 확인 필요 (userId={}, key={})", command.userId, key, it) }
        }
    }
}
