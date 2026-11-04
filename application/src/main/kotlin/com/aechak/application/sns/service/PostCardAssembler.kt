package com.aechak.application.sns.service

import com.aechak.application.file.usecase.FileUseCase
import com.aechak.application.sns.port.SnsFeedQueryPort
import com.aechak.application.sns.usecase.result.SnsPostAuthorResult
import com.aechak.application.sns.usecase.result.SnsPostDetailResult
import com.aechak.application.sns.usecase.result.SnsPostGridItemResult
import com.aechak.application.sns.usecase.result.SnsPostVideoResult
import com.aechak.application.user.pet.usecase.PetProfileUseCase
import com.aechak.domain.sns.follow.repository.FollowRepository
import com.aechak.domain.sns.post.enums.PostMediaKind
import com.aechak.domain.sns.post.enums.PostMediaType
import com.aechak.domain.sns.repost.repository.RepostRepository
import org.springframework.stereotype.Service

/** 게시물을 상세와 같은 카드나 프로필 탭 칸으로 조립한다. 피드 카드와 상세가 카드 매핑 하나를 쓴다 */
@Service
class PostCardAssembler(
    private val feedQueryPort: SnsFeedQueryPort,
    private val repostRepository: RepostRepository,
    private val followRepository: FollowRepository,
    private val petProfileUseCase: PetProfileUseCase,
    private val fileUseCase: FileUseCase,
) {
    /**
     * postIds 순서대로 카드를 만든다. 게시물 id 목록 하나로 배치 조회해 쿼리 수가 게시물 수와 상관없다.
     * viewerId가 null이면 비로그인이고 mutedIds는 보는 사람이 뮤트한 사용자 집합이다.
     */
    fun assemble(
        postIds: List<Long>,
        viewerId: Long?,
        mutedIds: Set<Long>,
    ): List<SnsPostDetailResult> {
        if (postIds.isEmpty()) return emptyList()
        val cards = feedQueryPort.findCards(postIds).associateBy { it.postId }
        val media = feedQueryPort.findMedia(postIds).groupBy { it.postId }
        val hashtags = feedQueryPort.findHashtagNames(postIds).groupBy({ it.postId }, { it.name })
        val pets = petProfileUseCase.getActivePetsByIds(cards.values.mapNotNull { it.petId }.toSet()).associateBy { it.petId }
        val reposted = viewerId?.let { repostRepository.findRepostedPostIdsAmong(it, postIds) }.orEmpty()
        val authorIds = cards.values.map { it.authorId }.toSet()
        val followees = viewerId?.let { followRepository.findFolloweeIdsAmong(it, authorIds) }.orEmpty()
        return postIds.mapNotNull { postId ->
            val card = cards[postId] ?: return@mapNotNull null
            val (photos, videos) = media[postId].orEmpty().partition { it.mediaKind == PostMediaKind.IMAGE }
            val isMine = card.authorId == viewerId
            SnsPostDetailResult(
                publicId = card.publicId,
                author =
                    SnsPostAuthorResult(
                        publicId = card.authorPublicId,
                        nickname = card.authorNickname,
                        profileImageUrl = fileUseCase.resolveMediaUrl(card.authorProfileImageKey),
                    ),
                pet = card.petId?.let(pets::get),
                caption = card.caption,
                hashtags = hashtags[postId].orEmpty(),
                photoUrls = photos.mapNotNull { fileUseCase.resolveMediaUrl(it.storageKey) },
                // 영상 행은 게시할 때 썸네일과 길이를 함께 저장한다
                video =
                    videos.firstOrNull()?.let {
                        SnsPostVideoResult(
                            url = fileUseCase.resolveMediaUrl(it.storageKey)!!,
                            thumbnailUrl = fileUseCase.resolveMediaUrl(it.thumbnailKey)!!,
                            durationMs = it.durationMs!!,
                        )
                    },
                // 좋아요와 댓글은 아직 없어 0이다
                likeCount = 0,
                commentCount = 0,
                repostCount = card.repostCount.toLong(),
                edited = card.editedAt != null,
                createdAt = card.createdAt,
                isMine = isMine,
                following = !isMine && card.authorId in followees,
                mutedByMe = card.authorId in mutedIds,
                reposted = postId in reposted,
                // 저장은 아직 없어 false다
                saved = false,
            )
        }
    }

    /** postIds 순서대로 프로필 탭 칸을 만든다. 카드 행과 미디어만 읽는다 */
    fun assembleCells(postIds: List<Long>): List<SnsPostGridItemResult> {
        if (postIds.isEmpty()) return emptyList()
        val cards = feedQueryPort.findCards(postIds).associateBy { it.postId }
        val media = feedQueryPort.findMedia(postIds).groupBy { it.postId }
        return postIds.mapNotNull { postId ->
            val card = cards[postId] ?: return@mapNotNull null
            val (photos, videos) = media[postId].orEmpty().partition { it.mediaKind == PostMediaKind.IMAGE }
            SnsPostGridItemResult(
                publicId = card.publicId,
                mediaType = card.mediaType,
                thumbnailUrl = fileUseCase.resolveMediaUrl(photos.firstOrNull()?.storageKey ?: videos.firstOrNull()?.thumbnailKey),
                photoCount = photos.size,
                captionPreview = card.caption?.takeIf { card.mediaType == PostMediaType.TEXT }?.takeCodePoints(CAPTION_PREVIEW_CODE_POINTS),
            )
        }
    }

    // 글자 수로 자르면 이모지 같은 서로게이트 쌍이 반으로 잘린다
    private fun String.takeCodePoints(count: Int): String = substring(0, offsetByCodePoints(0, minOf(count, codePointCount(0, length))))

    companion object {
        private const val CAPTION_PREVIEW_CODE_POINTS = 100
    }
}
