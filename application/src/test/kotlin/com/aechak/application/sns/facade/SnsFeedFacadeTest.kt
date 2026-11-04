package com.aechak.application.sns.facade

import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.application.file.usecase.FileUseCase
import com.aechak.application.file.usecase.command.DeleteFileCommand
import com.aechak.application.file.usecase.command.IssuePresignedUrlCommand
import com.aechak.application.file.usecase.command.PromoteFileCommand
import com.aechak.application.file.usecase.result.IssuePresignedUrlResult
import com.aechak.application.file.usecase.result.PromoteFileResult
import com.aechak.application.sns.port.SnsFeedQueryPort
import com.aechak.application.sns.port.SnsPopularSnapshotStore
import com.aechak.application.sns.port.SnsPostTab
import com.aechak.application.sns.port.SnsVisibilityQueryPort
import com.aechak.application.sns.port.view.SnsFeedEntryView
import com.aechak.application.sns.port.view.SnsFeedRepostView
import com.aechak.application.sns.port.view.SnsPairStateView
import com.aechak.application.sns.port.view.SnsPostCardView
import com.aechak.application.sns.port.view.SnsPostHashtagView
import com.aechak.application.sns.port.view.SnsPostMediaView
import com.aechak.application.sns.service.PostCardAssembler
import com.aechak.application.sns.service.SnsFeedService
import com.aechak.application.sns.service.SnsProfileService
import com.aechak.application.sns.service.SnsVisibilityService
import com.aechak.application.sns.support.SnsFeedCursorCodec
import com.aechak.application.sns.usecase.query.SnsFeedQuery
import com.aechak.application.user.pet.usecase.PetProfileUseCase
import com.aechak.application.user.pet.usecase.command.RegisterPetProfileCommand
import com.aechak.application.user.pet.usecase.command.UpdatePetProfileCommand
import com.aechak.application.user.pet.usecase.result.DefaultPetSummaryResult
import com.aechak.application.user.pet.usecase.result.DeletePetProfileResult
import com.aechak.application.user.pet.usecase.result.PetProfileListResult
import com.aechak.application.user.pet.usecase.result.PetProfileResult
import com.aechak.application.user.pet.usecase.result.RegisterPetProfileResult
import com.aechak.application.user.pet.usecase.result.RepresentativePetResult
import com.aechak.application.user.pet.usecase.result.SetDefaultPetResult
import com.aechak.application.user.user.usecase.UserUseCase
import com.aechak.application.user.user.usecase.command.SetNicknameCommand
import com.aechak.application.user.user.usecase.command.UpdateProfileCommand
import com.aechak.application.user.user.usecase.query.UserSearchQuery
import com.aechak.application.user.user.usecase.result.UserAuthorResult
import com.aechak.application.user.user.usecase.result.UserMeResult
import com.aechak.application.user.user.usecase.result.UserPublicProfileResult
import com.aechak.application.user.user.usecase.result.UserSummaryResult
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.sns.follow.repository.FollowRepository
import com.aechak.domain.sns.post.Post
import com.aechak.domain.sns.post.enums.PostMediaType
import com.aechak.domain.sns.post.repository.PostRepository
import com.aechak.domain.sns.repost.repository.RepostRepository
import com.aechak.domain.sns.stats.SnsUserStats
import com.aechak.domain.sns.stats.repository.SnsUserStatsRepository
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.dao.QueryTimeoutException
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * 계약: 인기 순위 저장소(Redis)가 실패해도 첫 페이지는 DB에서 계산한 순위로 한 페이지를 다음 커서 없이 주고 다음 페이지는 90002인지.
 * 통합 테스트의 Redis 컨테이너로는 연결 실패, 시간 초과, 남긴 직후 키가 없어지는 경우를 만들 수 없어 예외를 던지거나 남긴 키를 돌려주지 않는 저장소로 본다.
 * 깨지면 Redis 장애가 인기 피드를 500으로 만들거나 남기지 못한 순위의 커서를 내줘 다음 페이지가 다른 순위를 읽는다.
 */
class SnsFeedFacadeTest {
    private val port = FakeFeedQueryPort(ranking = listOf(3L, 2L, 1L))

    @Test
    fun `순위를 읽지 못하면 첫 페이지는 계산한 순위로 크기만큼 주고 다음 커서가 없다`() {
        val page = facade(FailingSnapshotStore(failOnFind = true)).popular(SnsFeedQuery(viewerId = null, cursor = null, size = 2))

        assertEquals(listOf("post-3", "post-2"), page.items.map { it.post.publicId }, "계산한 순위의 앞에서 크기만큼 줘야 한다")
        assertNull(page.nextCursor, "남기지 못한 순위의 커서를 주면 안 된다")
        assertFalse(page.hasNext, "다음 페이지가 없어야 한다")
    }

    @Test
    fun `순위를 남기지 못하면 첫 페이지는 계산한 순위로 크기만큼 주고 다음 커서가 없다`() {
        val page = facade(FailingSnapshotStore(failOnFind = false)).popular(SnsFeedQuery(viewerId = null, cursor = null, size = 2))

        assertEquals(listOf("post-3", "post-2"), page.items.map { it.post.publicId }, "계산한 순위의 앞에서 크기만큼 줘야 한다")
        assertNull(page.nextCursor, "남기지 못한 순위의 커서를 주면 안 된다")
        assertFalse(page.hasNext, "다음 페이지가 없어야 한다")
    }

    @Test
    fun `남긴 순위를 다시 읽을 때 키가 없으면 첫 페이지는 계산한 순위로 크기만큼 주고 다음 커서가 없다`() {
        val page = facade(LosingSnapshotStore()).popular(SnsFeedQuery(viewerId = null, cursor = null, size = 2))

        assertEquals(listOf("post-3", "post-2"), page.items.map { it.post.publicId }, "계산한 순위의 앞에서 크기만큼 줘야 한다")
        assertNull(page.nextCursor, "다음 페이지에 쓸 순위가 없으니 커서를 주면 안 된다")
        assertFalse(page.hasNext, "다음 페이지가 없어야 한다")
    }

    @Test
    fun `순위를 읽지 못하면 다음 페이지는 90002다`() {
        val cursor = SnsFeedCursorCodec.encodePopular(29_897_280L, 1)

        val error =
            assertFailsWith<BusinessException> {
                facade(FailingSnapshotStore(failOnFind = true)).popular(SnsFeedQuery(viewerId = null, cursor = cursor, size = 2))
            }

        assertEquals(CommonErrorCode.INVALID_CURSOR, error.errorCode, "다음 페이지는 첫 페이지부터 다시 부르게 90002여야 한다")
    }

    private fun facade(store: SnsPopularSnapshotStore) =
        SnsFeedFacade(
            SnsFeedService(port, UnusedPostRepository()),
            SnsVisibilityService(UnusedVisibilityQueryPort()),
            SnsProfileService(UnusedUserStatsRepository()),
            PostCardAssembler(port, UnusedRepostRepository(), UnusedFollowRepository(), NoPetProfileUseCase(), KeyUrlFileUseCase()),
            store,
            UnusedUserUseCase(),
            NoOpTransactionManager(),
        )
}

/** find나 saveIfAbsent에서 Redis 연결 실패나 시간 초과를 던진다. failOnFind가 false면 키가 없다고 답한 뒤 저장에서 실패한다 */
private class FailingSnapshotStore(
    private val failOnFind: Boolean,
) : SnsPopularSnapshotStore {
    override fun find(epochMinute: Long): List<Long>? = if (failOnFind) throw QueryTimeoutException("Redis command timed out") else null

    override fun saveIfAbsent(
        epochMinute: Long,
        postIds: List<Long>,
    ): Unit = throw DataAccessResourceFailureException("Unable to connect to Redis")
}

/** 저장은 성공하지만 다시 읽으면 키가 없다. 메모리가 모자라 Redis가 키를 지웠거나 재시작한 경우다 */
private class LosingSnapshotStore : SnsPopularSnapshotStore {
    override fun find(epochMinute: Long): List<Long>? = null

    override fun saveIfAbsent(
        epochMinute: Long,
        postIds: List<Long>,
    ) = Unit
}

/** 순위 쿼리는 정해 둔 순위를 돌려주고 모든 게시물을 볼 수 있다고 답한다 */
private class FakeFeedQueryPort(
    private val ranking: List<Long>,
) : SnsFeedQueryPort {
    override fun findFollowingPage(
        viewerId: Long,
        hiddenUserIds: Set<Long>,
        anchor: LocalDateTime,
        lastTime: LocalDateTime?,
        lastPostId: Long?,
        limit: Int,
    ): List<SnsFeedEntryView> = throw UnsupportedOperationException()

    override fun findLatestReposts(
        viewerId: Long,
        hiddenUserIds: Set<Long>,
        anchor: LocalDateTime,
        postIds: Collection<Long>,
    ): List<SnsFeedRepostView> = throw UnsupportedOperationException()

    override fun findExplorePage(
        viewerId: Long?,
        hiddenUserIds: Set<Long>,
        from: LocalDateTime,
        anchor: LocalDateTime,
        lastCreatedAt: LocalDateTime?,
        lastPostId: Long?,
        limit: Int,
    ): List<SnsFeedEntryView> = throw UnsupportedOperationException()

    override fun findPopularRanking(
        from: LocalDateTime,
        to: LocalDateTime,
        repostWeight: Int,
        limit: Int,
    ): List<Long> = ranking

    override fun findUserPostPage(
        authorId: Long,
        tab: SnsPostTab,
        lastCreatedAt: LocalDateTime?,
        lastPostId: Long?,
        limit: Int,
    ): List<SnsFeedEntryView> = throw UnsupportedOperationException()

    override fun findVisiblePostIds(
        postIds: Collection<Long>,
        hiddenUserIds: Set<Long>,
    ): Set<Long> = postIds.toSet()

    override fun findCards(postIds: Collection<Long>): List<SnsPostCardView> =
        postIds.map {
            SnsPostCardView(
                postId = it,
                publicId = "post-$it",
                authorId = 100 + it,
                authorPublicId = "author-$it",
                authorNickname = "집사$it",
                authorProfileImageKey = null,
                petId = null,
                mediaType = PostMediaType.TEXT,
                caption = "캡션",
                editedAt = null,
                createdAt = LocalDateTime.of(2026, 11, 5, 9, 0),
                repostCount = it.toInt(),
            )
        }

    override fun findMedia(postIds: Collection<Long>): List<SnsPostMediaView> = emptyList()

    override fun findHashtagNames(postIds: Collection<Long>): List<SnsPostHashtagView> = emptyList()
}

private class NoPetProfileUseCase : PetProfileUseCase {
    override fun registerPet(command: RegisterPetProfileCommand): RegisterPetProfileResult = throw UnsupportedOperationException()

    override fun getPets(userId: Long): PetProfileListResult = throw UnsupportedOperationException()

    override fun getDefaultPetSummaries(userIds: Collection<Long>): List<DefaultPetSummaryResult> = throw UnsupportedOperationException()

    override fun getActivePetsByIds(petIds: Collection<Long>): List<PetProfileResult> = emptyList()

    override fun findRepresentativePet(userId: Long): RepresentativePetResult? = throw UnsupportedOperationException()

    override fun updatePet(command: UpdatePetProfileCommand): PetProfileResult = throw UnsupportedOperationException()

    override fun deletePet(
        userId: Long,
        petId: Long,
    ): DeletePetProfileResult = throw UnsupportedOperationException()

    override fun setDefaultPet(
        userId: Long,
        petId: Long,
    ): SetDefaultPetResult = throw UnsupportedOperationException()
}

private class KeyUrlFileUseCase : FileUseCase {
    override fun issuePresignedUrl(command: IssuePresignedUrlCommand): IssuePresignedUrlResult = throw UnsupportedOperationException()

    override fun promote(command: PromoteFileCommand): PromoteFileResult = throw UnsupportedOperationException()

    override fun resolveMediaUrl(key: String?): String? = key

    override fun issueDownloadUrl(
        key: String,
        purpose: UploadPurpose,
    ): String = throw UnsupportedOperationException()

    override fun delete(command: DeleteFileCommand) = throw UnsupportedOperationException()
}

private class UnusedPostRepository : PostRepository {
    override fun save(post: Post): Post = throw UnsupportedOperationException()

    override fun findByPublicId(publicId: String): Post? = throw UnsupportedOperationException()

    override fun findByPublicIdForUpdate(publicId: String): Post? = throw UnsupportedOperationException()

    override fun findByAuthorIdAndIdempotencyKey(
        authorId: Long,
        idempotencyKey: String,
    ): Post? = throw UnsupportedOperationException()
}

private class UnusedVisibilityQueryPort : SnsVisibilityQueryPort {
    override fun findPairState(
        viewerId: Long,
        targetId: Long,
    ): SnsPairStateView = throw UnsupportedOperationException()

    override fun findViewerFilter(viewerId: Long): Set<Long> = throw UnsupportedOperationException()

    override fun findMutedIds(viewerId: Long): Set<Long> = throw UnsupportedOperationException()
}

private class UnusedRepostRepository : RepostRepository {
    override fun insertIfAbsent(
        reposterId: Long,
        postId: Long,
        at: LocalDateTime,
    ): Boolean = throw UnsupportedOperationException()

    override fun delete(
        reposterId: Long,
        postId: Long,
    ): Int = throw UnsupportedOperationException()

    override fun findRepostedPostIdsAmong(
        reposterId: Long,
        postIds: Collection<Long>,
    ): Set<Long> = throw UnsupportedOperationException()

    override fun deleteBetween(
        reposterId: Long,
        authorId: Long,
    ): List<Long> = throw UnsupportedOperationException()
}

private class UnusedFollowRepository : FollowRepository {
    override fun insertIfAbsent(
        followerId: Long,
        followeeId: Long,
    ): Boolean = throw UnsupportedOperationException()

    override fun delete(
        followerId: Long,
        followeeId: Long,
    ): Int = throw UnsupportedOperationException()

    override fun findFolloweeIdsAmong(
        followerId: Long,
        followeeIds: Collection<Long>,
    ): Set<Long> = throw UnsupportedOperationException()
}

private class UnusedUserStatsRepository : SnsUserStatsRepository {
    override fun findByUserId(userId: Long): SnsUserStats? = throw UnsupportedOperationException()

    override fun lockInUserIdOrder(
        userId: Long,
        otherUserId: Long,
    ) = throw UnsupportedOperationException()

    override fun increaseFollowerCount(userId: Long): Int = throw UnsupportedOperationException()

    override fun decreaseFollowerCount(userId: Long): Int = throw UnsupportedOperationException()

    override fun increaseFollowingCount(userId: Long): Int = throw UnsupportedOperationException()

    override fun decreaseFollowingCount(userId: Long): Int = throw UnsupportedOperationException()

    override fun increasePostCount(userId: Long): Int = throw UnsupportedOperationException()

    override fun decreasePostCount(userId: Long): Int = throw UnsupportedOperationException()
}

private class UnusedUserUseCase : UserUseCase {
    override fun checkNickname(
        userId: Long,
        nickname: String,
    ): Boolean = throw UnsupportedOperationException()

    override fun setNickname(command: SetNicknameCommand): UserMeResult = throw UnsupportedOperationException()

    override fun getMe(userId: Long): UserMeResult = throw UnsupportedOperationException()

    override fun updateProfile(command: UpdateProfileCommand): UserMeResult = throw UnsupportedOperationException()

    override fun searchUsers(query: UserSearchQuery): List<UserSummaryResult> = throw UnsupportedOperationException()

    override fun getAuthors(userIds: Collection<Long>): Map<Long, UserAuthorResult> = throw UnsupportedOperationException()

    override fun findPublicProfile(publicId: String): UserPublicProfileResult? = throw UnsupportedOperationException()

    override fun findPublicProfileById(userId: Long): UserPublicProfileResult? = throw UnsupportedOperationException()

    override fun registerFromSocial(): Long = throw UnsupportedOperationException()
}

private class NoOpTransactionManager : AbstractPlatformTransactionManager() {
    override fun doGetTransaction(): Any = Any()

    override fun doBegin(
        transaction: Any,
        definition: TransactionDefinition,
    ) = Unit

    override fun doCommit(status: DefaultTransactionStatus) = Unit

    override fun doRollback(status: DefaultTransactionStatus) = Unit
}
