package com.aechak.application.sns.facade

import com.aechak.application.file.error.FileErrorCode
import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.application.file.usecase.FileUseCase
import com.aechak.application.file.usecase.command.DeleteFileCommand
import com.aechak.application.file.usecase.command.PromoteFileCommand
import com.aechak.application.moderation.usecase.ModerationUseCase
import com.aechak.application.moderation.usecase.result.ModerationDecision
import com.aechak.application.sns.service.HashtagService
import com.aechak.application.sns.service.PostAccessService
import com.aechak.application.sns.service.PostCardAssembler
import com.aechak.application.sns.service.PostCommandService
import com.aechak.application.sns.service.RepostService
import com.aechak.application.sns.usecase.SnsPostUseCase
import com.aechak.application.sns.usecase.command.CreatePostCommand
import com.aechak.application.sns.usecase.command.RepostPostCommand
import com.aechak.application.sns.usecase.command.UpdatePostCommand
import com.aechak.application.sns.usecase.result.CreatePostResult
import com.aechak.application.sns.usecase.result.SnsHashtagSearchResult
import com.aechak.application.sns.usecase.result.SnsPostDetailResult
import com.aechak.application.user.pet.usecase.PetProfileUseCase
import com.aechak.application.user.user.usecase.UserUseCase
import com.aechak.common.error.BusinessException
import com.aechak.domain.sns.error.SnsErrorCode
import com.aechak.domain.sns.hashtag.Hashtag
import com.aechak.domain.sns.post.Post
import com.aechak.domain.sns.post.repository.PostRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime

/**
 * SNS 게시물 진입점. 금지어 판정은 게시물만 쓰므로 관계 쪽 SnsFacade와 나눠 둔다.
 * 승격과 tmp 키 삭제는 트랜잭션 밖에서 한다.
 */
@Service
class SnsPostFacade(
    private val postCommandService: PostCommandService,
    private val postAccessService: PostAccessService,
    private val hashtagService: HashtagService,
    private val repostService: RepostService,
    private val postCardAssembler: PostCardAssembler,
    private val postRepository: PostRepository,
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

        // shortcut: 서버가 영상을 변환하지 않고 길이는 FE가 보낸 durationMs를 믿는다. 고친 클라이언트는 100MB 안에서 60초를 넘는 영상을 올릴 수 있고
        // 아이폰 HEVC .mov는 일부 브라우저에서 재생되지 않을 수 있다. 재생되지 않는 영상 신고가 들어오거나 앱 출시로 녹화 형식이 늘어나면
        // 영상 변환을 추가하고 게시물 처리 상태에 맞춰 계약과 피드 노출 규칙을 함께 고친다
        val video = command.video?.let { Post.VideoSpec(it.key, it.thumbnailKey, it.durationMs) }
        // 캡션, 첨부, 태그의 형식 규칙을 모두 본 뒤에 금지어를 판정한다
        val draftCaption = Post.validateDraft(command.caption, command.photoKeys, video)
        val hashtags = Post.normalizeHashtags(command.hashtags)
        val caption = draftCaption?.let(::moderate)
        if (hashtags.any(moderationUseCase::containsBannedWord)) throw BusinessException(SnsErrorCode.SNS_HASHTAG_BANNED_WORD)
        val petId =
            petProfileUseCase
                .getPets(command.userId)
                .pets
                .firstOrNull { it.isDefault }
                ?.petId
        // 게시물 사진과 영상은 승격마다 새 영구 키라 이 요청이 승격한 파일은 이 요청만 쓴다
        val promoted = mutableListOf<String>()
        val photoKeys: List<String>
        val promotedVideo: Post.VideoSpec?
        try {
            photoKeys = command.photoKeys.map { promote(command.userId, it, UploadPurpose.SNS_POST_IMAGE, promoted) }
            promotedVideo =
                video?.let {
                    it.copy(
                        key = promote(command.userId, it.key, UploadPurpose.SNS_POST_VIDEO, promoted),
                        thumbnailKey = it.thumbnailKey?.let { key -> promote(command.userId, key, UploadPurpose.SNS_POST_IMAGE, promoted) },
                    )
                }
        } catch (e: Exception) {
            deletePromoted(command.userId, promoted)
            // 같은 키의 늦은 동시 요청은 앞선 요청이 tmp 키를 지운 뒤라 100001을 받는다
            if (e is BusinessException && e.errorCode == FileErrorCode.FILE_NOT_FOUND) return findIdempotent(command) ?: throw e
            throw e
        }

        var committing = false
        val result =
            try {
                tx.execute {
                    postCommandService
                        .create(command.userId, caption, photoKeys, promotedVideo, hashtags, petId, command.idempotencyKey)
                        .also { committing = true }
                }!!
            } catch (e: Exception) {
                // 커밋 중에 난 예외는 저장됐는지 알 수 없어 파일을 남긴다. 같은 키로 다시 보내면 저장된 게시물을 찾는다
                if (committing) {
                    log.error("게시 커밋 결과를 알 수 없어 승격 파일을 남김, 수동 확인 필요 (userId={}, keys={})", command.userId, promoted, e)
                    throw e
                }
                deletePromoted(command.userId, promoted)
                // 동시 요청은 사전 조회를 둘 다 통과한다. (작성자, 멱등키) UNIQUE가 심판이고 진 쪽은 먼저 생긴 게시물을 돌려준다.
                // try가 tx.execute를 감싸야 함 — 안에서 잡으면 롤백 전용 마킹 때문에 커밋에서 다시 터짐
                if (e is DataIntegrityViolationException) return findIdempotent(command) ?: throw e
                throw e
            }
        deleteTmpKeys(command)
        return result
    }

    override fun updatePost(command: UpdatePostCommand) {
        // 규칙은 게시물 조회보다 먼저 본다. 없는 게시물이어도 규칙을 어긴 수정은 404가 아니라 400이다
        val draftCaption = Post.normalizeCaption(command.caption)
        val hashtags = Post.normalizeHashtags(command.hashtags)
        val caption = draftCaption?.let(::moderate)
        if (hashtags.any(moderationUseCase::containsBannedWord)) throw BusinessException(SnsErrorCode.SNS_HASHTAG_BANNED_WORD)
        tx.execute { postCommandService.update(command.publicId, command.userId, caption, hashtags) }
    }

    @Transactional(readOnly = true)
    override fun getPost(
        publicId: String,
        viewerId: Long?,
    ): SnsPostDetailResult {
        val post = postAccessService.requirePublished(publicId)
        postAccessService.requireActiveAuthor(userUseCase.findPublicProfileById(post.authorId))
        val pair = postAccessService.requireNotBlocked(post.authorId, viewerId)
        val mutedIds = if (pair.mutedByMe) setOf(post.authorId) else emptySet()
        return postCardAssembler.assemble(listOf(post.id), viewerId, mutedIds).single()
    }

    @Transactional(readOnly = true)
    override fun searchHashtags(
        query: String?,
        size: Int,
    ): SnsHashtagSearchResult {
        val cleaned = Hashtag.clean(query.orEmpty())
        return hashtagService.search(cleaned, moderationUseCase.containsBannedWord(cleaned), size)
    }

    override fun repost(command: RepostPostCommand) {
        tx.execute {
            val post = postAccessService.requirePublished(command.publicId)
            if (post.authorId == command.userId) throw BusinessException(SnsErrorCode.SNS_REPOST_OWN_POST)
            postAccessService.requireActiveAuthor(userUseCase.findPublicProfileById(post.authorId))
            repostService.repost(command.userId, post.id, post.authorId, LocalDateTime.now())
        }
    }

    // 끄기는 상태, 작성자, 관계를 보지 않아 볼 수 없게 된 원글의 리포스트도 지운다
    override fun unrepost(command: RepostPostCommand) {
        tx.execute {
            val post = postRepository.findByPublicId(command.publicId) ?: throw BusinessException(SnsErrorCode.SNS_POST_UNAVAILABLE)
            repostService.unrepost(command.userId, post.id)
        }
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

    /** 승격한 영구 키를 promoted에 모은다. 게시에 실패하면 이 목록으로 승격 파일을 지운다 */
    private fun promote(
        userId: Long,
        tmpKey: String,
        purpose: UploadPurpose,
        promoted: MutableList<String>,
    ): String = fileUseCase.promote(PromoteFileCommand(tmpKey, userId, purpose)).key.also(promoted::add)

    /**
     * 저장하지 못한 요청이 승격한 파일을 지운다. 승격마다 새 키라 다른 게시물의 파일이 아니다.
     * 삭제는 용도로 버킷만 고르고 영상도 사진과 같은 MEDIA 버킷이라 사진 용도로 지운다.
     */
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
        (command.photoKeys + listOfNotNull(command.video?.key, command.video?.thumbnailKey)).forEach { key ->
            runCatching { fileUseCase.delete(DeleteFileCommand(key, UploadPurpose.SNS_POST_IMAGE)) }
                .onFailure { log.error("게시 후 tmp 키 삭제 실패, 수동 확인 필요 (userId={}, key={})", command.userId, key, it) }
        }
    }
}
