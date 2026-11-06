package com.aechak.api.sns

import com.aechak.domain.user.user.User
import org.hibernate.SessionFactory
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 통합: 팔로잉 피드가 나와 내가 팔로우한 사용자의 원글과 리포스트를 원글마다 가장 늦은 이벤트 시각에 한 번만 싣고
 * 그 시각이 리포스트면 리포스트한 사람을 표시하는지, 숨김 사용자를 원천과 작성자 양쪽에서 빼고 커서가 묶은 뒤의 시각으로 이어지는지.
 * 깨지면 같은 원글이 여러 번 나오거나 다음 페이지에 옛 시각으로 다시 나오거나 차단, 뮤트 상대의 글이 리포스트로 들어온다.
 */
class FollowingFeedIntegrationTest : SnsPostIntegrationTestBase() {
    @Test
    fun `팔로우한 사용자가 리포스트한 원글은 리포스트 표시와 함께 나온다`() {
        val viewerId = onboardedUser()
        val reposterId = onboardedUser()
        relate(viewerId, reposterId, "follow")
        val publicId = createdPublicId(onboardedUser())
        repost(publicId, reposterId).andExpect(status().isCreated)

        assertEquals(listOf(publicId to publicIdOf(reposterId)), feedPage("following", viewerId).entries(), "리포스트한 사람과 함께 나와야 한다")
    }

    @Test
    fun `같은 원글을 여러 사람이 리포스트하면 가장 늦은 리포스트로 한 번만 나온다`() {
        val viewerId = onboardedUser()
        val (first, last) = List(2) { onboardedUser().also { relate(viewerId, it, "follow") } }
        val publicId = createdPublicId(onboardedUser())
        repost(publicId, first).andExpect(status().isCreated)
        repost(publicId, last).andExpect(status().isCreated)

        assertEquals(listOf(publicId to publicIdOf(last)), feedPage("following", viewerId).entries(), "가장 늦게 리포스트한 사람으로 한 번만 나와야 한다")
    }

    @Test
    fun `원글 작성자와 리포스트한 사람을 모두 팔로우하면 리포스트 시각 자리에 한 번만 나온다`() {
        val viewerId = onboardedUser()
        val authorId = onboardedUser()
        val reposterId = onboardedUser()
        relate(viewerId, authorId, "follow")
        relate(viewerId, reposterId, "follow")
        val reposted = createdPublicId(authorId)
        val between = createdPublicId(authorId)
        repost(reposted, reposterId).andExpect(status().isCreated)

        assertEquals(
            listOf(reposted to publicIdOf(reposterId), between to null),
            feedPage("following", viewerId).entries(),
            "리포스트 시각으로 한 번 나오고 그 사이의 글이 뒤에 와야 한다",
        )
    }

    @Test
    fun `리포스트를 취소했다가 다시 하면 다시 한 시각 자리로 옮긴다`() {
        val viewerId = onboardedUser()
        val reposterId = onboardedUser()
        val authorId = onboardedUser()
        relate(viewerId, reposterId, "follow")
        relate(viewerId, authorId, "follow")
        val reposted = createdPublicId(onboardedUser())
        repost(reposted, reposterId).andExpect(status().isCreated)
        backdateRepost(reposterId, reposted, LocalDateTime.now().minusHours(1))
        val later = createdPublicId(authorId)
        assertEquals(
            listOf(later to null, reposted to publicIdOf(reposterId)),
            feedPage("following", viewerId).entries(),
            "처음 리포스트한 자리여야 한다",
        )

        unrepost(reposted, reposterId).andExpect(status().isNoContent)
        repost(reposted, reposterId).andExpect(status().isCreated)

        assertEquals(
            listOf(reposted to publicIdOf(reposterId), later to null),
            feedPage("following", viewerId).entries(),
            "다시 리포스트한 자리로 와야 한다",
        )
    }

    @Test
    fun `내 글과 내 리포스트가 나온다`() {
        val viewerId = onboardedUser()
        val mine = createdPublicId(viewerId)
        val reposted = createdPublicId(onboardedUser())
        repost(reposted, viewerId).andExpect(status().isCreated)

        assertEquals(
            listOf(reposted to publicIdOf(viewerId), mine to null),
            feedPage("following", viewerId).entries(),
            "내 원글과 내 리포스트가 나와야 한다",
        )
    }

    @Test
    fun `팔로우가 없고 내 글만 있으면 내 글이 나온다`() {
        val viewerId = onboardedUser()
        val mine = createdPublicId(viewerId)
        createdPublicId(onboardedUser())

        assertEquals(listOf(mine to null), feedPage("following", viewerId).entries(), "팔로우가 없어도 내 글은 나와야 한다")
    }

    @Test
    fun `원천에 글도 리포스트도 없으면 빈 목록이다`() {
        val viewerId = onboardedUser()
        relate(viewerId, onboardedUser(), "follow")
        createdPublicId(onboardedUser())

        val page = feedPage("following", viewerId)

        assertEquals(emptyList(), page.entries(), "빈 목록이어야 한다")
        assertEquals(false, page["hasNext"], "빈 목록은 다음 페이지가 없어야 한다")
    }

    @Test
    fun `가장 늦게 리포스트한 사람을 언팔로우하거나 뮤트하거나 그 사람이 취소하면 남은 리포스트로 나온다`() {
        val viewerId = onboardedUser()
        val earlier = onboardedUser()
        val (unfollowed, muted, canceled) = List(3) { onboardedUser() }
        listOf(earlier, unfollowed, muted, canceled).forEach { relate(viewerId, it, "follow") }
        val posts = List(3) { createdPublicId(onboardedUser()) }
        posts.forEach { repost(it, earlier).andExpect(status().isCreated) }
        repost(posts[0], unfollowed).andExpect(status().isCreated)
        repost(posts[1], muted).andExpect(status().isCreated)
        repost(posts[2], canceled).andExpect(status().isCreated)
        posts.forEachIndexed { index, publicId -> backdateRepost(earlier, publicId, LocalDateTime.now().minusHours(3L - index)) }

        unrelate(viewerId, unfollowed, "follow")
        relate(viewerId, muted, "mute")
        unrepost(posts[2], canceled).andExpect(status().isNoContent)

        assertEquals(
            posts.reversed().map { it to publicIdOf(earlier) },
            feedPage("following", viewerId).entries(),
            "남은 팔로우 상대의 리포스트 시각과 표시로 나와야 한다",
        )
    }

    @Test
    fun `팔로우 상대가 리포스트한 차단 상대와 뮤트 상대의 글은 나오지 않는다`() {
        val viewerId = onboardedUser()
        val reposterId = onboardedUser()
        relate(viewerId, reposterId, "follow")
        val (blockedByMe, blockingMe, muted) = List(3) { onboardedUser() }
        listOf(blockedByMe, blockingMe, muted).forEach { repost(createdPublicId(it), reposterId).andExpect(status().isCreated) }
        relate(viewerId, blockedByMe, "block")
        relate(blockingMe, viewerId, "block")
        relate(viewerId, muted, "mute")

        assertEquals(emptyList(), feedPage("following", viewerId).entries(), "숨긴 작성자의 글은 리포스트로도 나오면 안 된다")
    }

    @Test
    fun `뮤트한 팔로우 상대의 글과 리포스트가 빠지고 뮤트를 풀면 다시 나온다`() {
        val viewerId = onboardedUser()
        val followed = onboardedUser()
        relate(viewerId, followed, "follow")
        val own = createdPublicId(followed)
        val reposted = createdPublicId(onboardedUser())
        repost(reposted, followed).andExpect(status().isCreated)
        relate(viewerId, followed, "mute")
        assertEquals(emptyList(), feedPage("following", viewerId).entries(), "뮤트한 팔로우 상대의 글과 리포스트는 빠져야 한다")

        unrelate(viewerId, followed, "mute")

        assertEquals(listOf(reposted to publicIdOf(followed), own to null), feedPage("following", viewerId).entries(), "뮤트를 풀면 다시 나와야 한다")
    }

    @Test
    fun `지운 원글과 탈퇴한 작성자의 원글은 리포스트로도 나오지 않는다`() {
        val viewerId = onboardedUser()
        val reposterId = onboardedUser()
        relate(viewerId, reposterId, "follow")
        val authorId = onboardedUser()
        val withdrawn = onboardedUser()
        val deleted = createdPublicId(authorId)
        val withdrawnPost = createdPublicId(withdrawn)
        listOf(deleted, withdrawnPost).forEach { repost(it, reposterId).andExpect(status().isCreated) }
        deletePost(deleted, authorId).andExpect(status().isNoContent)
        tx.execute { em.find(User::class.java, withdrawn).withdraw() }

        assertEquals(emptyList(), feedPage("following", viewerId).entries(), "볼 수 없는 원글은 리포스트로도 나오면 안 된다")
    }

    @Test
    fun `이용 제한과 기간이 남은 임시 정지 팔로우 상대의 글과 리포스트가 나온다`() {
        val viewerId = onboardedUser()
        val restricted = onboardedUser()
        val suspended = onboardedUser()
        relate(viewerId, restricted, "follow")
        relate(viewerId, suspended, "follow")
        val own = createdPublicId(restricted)
        val reposted = createdPublicId(onboardedUser())
        repost(reposted, suspended).andExpect(status().isCreated)
        tx.execute {
            em.find(User::class.java, restricted).restrict()
            em.find(User::class.java, suspended).suspendUntil(LocalDateTime.now().plusDays(3))
        }

        assertEquals(
            listOf(reposted to publicIdOf(suspended), own to null),
            feedPage("following", viewerId).entries(),
            "정지 상태 사용자의 글과 리포스트도 나와야 한다",
        )
    }

    @Test
    fun `뮤트한 작성자의 글을 내가 리포스트해도 내 팔로잉 피드에는 나오지 않는다`() {
        val viewerId = onboardedUser()
        val muted = onboardedUser()
        val publicId = createdPublicId(muted)
        relate(viewerId, muted, "mute")
        repost(publicId, viewerId).andExpect(status().isCreated)

        assertEquals(emptyList(), feedPage("following", viewerId).entries(), "뮤트한 작성자의 글은 내 리포스트로도 나오면 안 된다")
    }

    @Test
    fun `크기 1로 넘기면 같은 원글이 두 번 나오지 않고 첫 페이지 뒤의 글과 리포스트는 들어오지 않는다`() {
        val viewerId = onboardedUser()
        val (first, second) = List(2) { onboardedUser().also { relate(viewerId, it, "follow") } }
        val older = createdPublicId(first)
        val newer = createdPublicId(second)
        repost(older, second).andExpect(status().isCreated)
        val page1 = feedPage("following", viewerId, size = 1)
        assertEquals(listOf(older to publicIdOf(second)), page1.entries(), "첫 페이지는 리포스트 시각의 원글이어야 한다")

        createdPublicId(first)
        repost(newer, first).andExpect(status().isCreated)
        val page2 = feedPage("following", viewerId, cursor = page1["nextCursor"] as String, size = 1)

        assertEquals(listOf(newer to null), page2.entries(), "기준 시각 뒤의 글과 리포스트가 들어오면 안 된다")
        assertEquals(false, page2["hasNext"], "같은 원글이 다시 나오면 안 된다")
    }

    @Test
    fun `커서 앞뒤에 모두 이벤트가 있는 원글은 다음 페이지에 원글 시각으로 다시 나오지 않는다`() {
        val viewerId = onboardedUser()
        val authorId = onboardedUser()
        val reposterId = onboardedUser()
        relate(viewerId, authorId, "follow")
        relate(viewerId, reposterId, "follow")
        val reposted = createdPublicId(authorId)
        val middle = createdPublicId(authorId)
        repost(reposted, reposterId).andExpect(status().isCreated)

        val page1 = feedPage("following", viewerId, size = 1)
        val page2 = feedPage("following", viewerId, cursor = page1["nextCursor"] as String, size = 1)

        assertEquals(listOf(reposted to publicIdOf(reposterId)), page1.entries(), "첫 페이지는 리포스트 시각의 원글이어야 한다")
        assertEquals(listOf(middle to null), page2.entries(), "다음 페이지는 그 사이의 글이어야 한다")
        assertEquals(false, page2["hasNext"], "앞 페이지에 나온 원글이 원글 시각으로 다시 나오면 안 된다")
    }

    @Test
    fun `앞 페이지의 마지막 원글을 지워도 다음 페이지를 이어서 받는다`() {
        val viewerId = onboardedUser()
        val authorId = onboardedUser()
        relate(viewerId, authorId, "follow")
        val (oldest, middle, newest) = List(3) { createdPublicId(authorId) }
        val page1 = feedPage("following", viewerId, size = 2)
        assertEquals(listOf(newest, middle), page1.postIds(), "첫 페이지는 최근 글 둘이어야 한다")

        deletePost(middle, authorId).andExpect(status().isNoContent)

        assertEquals(
            listOf(oldest),
            feedPage("following", viewerId, cursor = page1["nextCursor"] as String, size = 2).postIds(),
            "지운 글 뒤의 글이 이어져야 한다",
        )
    }

    @Test
    fun `20개 페이지와 5개 페이지의 쿼리 수가 같다`() {
        val viewerId = onboardedUser()
        val authors = List(5) { onboardedUser().also { defaultPet(it) } }
        authors.forEach { relate(viewerId, it, "follow") }
        val posts =
            List(25) {
                val authorId = authors[it % authors.size]
                createdPublicId(authorId, postBody(photoKeys = listOf(tmpPhotoKey(authorId)), hashtags = listOf("산책")))
            }
        // 셋에 하나는 다른 팔로우 상대가 리포스트해 리포스트 표시가 있는 카드가 섞인다
        posts.forEachIndexed { index, publicId ->
            if (index % 3 == 0) repost(publicId, authors[(index + 1) % authors.size]).andExpect(status().isCreated)
        }
        val statistics = em.entityManagerFactory.unwrap(SessionFactory::class.java).statistics
        statistics.isStatisticsEnabled = true

        statistics.clear()
        val twentyPage = feedPage("following", viewerId, size = 20)
        val twenty = statistics.prepareStatementCount
        statistics.clear()
        val fivePage = feedPage("following", viewerId, size = 5)
        val five = statistics.prepareStatementCount

        assertEquals(20, twentyPage.entries().size, "20개 페이지여야 한다")
        assertEquals(5, fivePage.entries().size, "5개 페이지여야 한다")
        assertTrue(fivePage.entries().any { it.second != null }, "리포스트 표시가 있는 페이지여야 한다")
        assertEquals(five, twenty, "목록, 리포스트 표시, 카드 조립의 쿼리 수가 게시물 수와 상관없어야 한다")
    }

    /** 카드마다 게시물 publicId와 리포스트한 사람의 publicId. 리포스트 표시가 없으면 null이다 */
    private fun Map<String, Any?>.entries(): List<Pair<String, String?>> =
        feedItems().map { (it["post"] as Map<*, *>)["publicId"] as String to (it["repostedBy"] as Map<*, *>?)?.get("publicId") as String? }
}
