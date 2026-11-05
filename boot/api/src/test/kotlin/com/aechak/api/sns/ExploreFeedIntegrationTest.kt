package com.aechak.api.sns

import com.aechak.application.sns.port.SnsFollowDirection
import com.aechak.application.sns.support.FollowCursorCodec
import com.aechak.application.sns.support.SnsFeedCursorCodec
import com.aechak.domain.user.user.User
import org.hibernate.SessionFactory
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 통합: 탐색 피드가 기준 시각에서 7일 안의 원글을 최신순 카드로 주고(20개에 못 미치면 7일이 지난 글을 잇고) 로그인이면 나, 팔로우,
 * 차단, 뮤트 작성자를 빼며 커서가 첫 페이지의 기준 시각을 지키는지, 카드가 상세와 같은 값이고 쿼리 수가 게시물 수와 상관없는지.
 * 깨지면 탐색에 내 글이나 숨긴 사용자의 글이 나오거나 글이 적을 때 7일이 지난 글로 채우지 않거나 내리는 동안 글이 겹치고 빠지거나
 * 카드마다 쿼리가 늘어난다.
 */
class ExploreFeedIntegrationTest : SnsPostIntegrationTestBase() {
    @Test
    fun `비로그인은 모든 사용자의 원글을 최신순으로 받고 리포스트 표시가 없다`() {
        val first = createdPublicId(onboardedUser())
        val second = createdPublicId(onboardedUser())
        val third = createdPublicId(onboardedUser())

        val page = feedPage("explore")

        assertEquals(listOf(third, second, first), page.postIds(), "모든 사용자의 원글이 최신순이어야 한다")
        assertTrue(page.feedItems().all { it.containsKey("repostedBy") && it["repostedBy"] == null }, "탐색 피드는 repostedBy가 null이어야 한다")
        assertEquals(setOf("items", "nextCursor", "hasNext"), page.keys, "피드 응답에는 totalCount가 없어야 한다")
    }

    @Test
    fun `로그인이면 내 글, 팔로우한 사용자, 차단 양방향, 뮤트, 지운 글, 운영자가 지운 글, 탈퇴 작성자의 글이 빠진다`() {
        val viewerId = onboardedUser()
        val followed = onboardedUser()
        val blockedByMe = onboardedUser()
        val blockingMe = onboardedUser()
        val muted = onboardedUser()
        val withdrawn = onboardedUser()
        val authorId = onboardedUser()
        val mine = createdPublicId(viewerId)
        val followedPost = createdPublicId(followed)
        val blockedPost = createdPublicId(blockedByMe)
        val blockingPost = createdPublicId(blockingMe)
        val mutedPost = createdPublicId(muted)
        createdPublicId(withdrawn)
        val deleted = createdPublicId(authorId)
        deletePost(deleted, authorId).andExpect(status().isNoContent)
        val operatorDeleted = createdPublicId(authorId)
        jdbcTemplate.update("update sns_posts set status = 'DELETED', deletion_reason = 'OPERATOR' where public_id = ?", operatorDeleted)
        val visible = createdPublicId(authorId)
        relate(viewerId, followed, "follow")
        relate(viewerId, blockedByMe, "block")
        relate(blockingMe, viewerId, "block")
        relate(viewerId, muted, "mute")
        tx.execute { em.find(User::class.java, withdrawn).withdraw() }

        assertEquals(listOf(visible), feedPage("explore", viewerId).postIds(), "보는 사람 기준으로 숨길 글이 모두 빠져야 한다")
        assertEquals(
            listOf(visible, mutedPost, blockingPost, blockedPost, followedPost, mine),
            feedPage("explore").postIds(),
            "비로그인은 지운 글과 탈퇴 작성자의 글만 빠져야 한다",
        )
    }

    @Test
    fun `탐색에서 본 사용자를 팔로우한 뒤 다시 부르면 그 사용자의 글이 빠진다`() {
        val viewerId = onboardedUser()
        val authorId = onboardedUser()
        val publicId = createdPublicId(authorId)
        assertEquals(listOf(publicId), feedPage("explore", viewerId).postIds(), "팔로우 전에는 나와야 한다")

        relate(viewerId, authorId, "follow")

        assertEquals(emptyList(), feedPage("explore", viewerId).postIds(), "팔로우한 사용자의 글은 빠져야 한다")
    }

    @Test
    fun `나를 뮤트한 사용자의 글은 그대로 나온다`() {
        val viewerId = onboardedUser()
        val authorId = onboardedUser()
        val publicId = createdPublicId(authorId)
        relate(authorId, viewerId, "mute")

        assertEquals(listOf(publicId), feedPage("explore", viewerId).postIds(), "뮤트는 한 방향이라 나를 뮤트한 사용자의 글은 나와야 한다")
    }

    @Test
    fun `차단을 풀면 그 사용자의 글이 다시 나온다`() {
        val viewerId = onboardedUser()
        val authorId = onboardedUser()
        val publicId = createdPublicId(authorId)
        relate(viewerId, authorId, "block")
        assertEquals(emptyList(), feedPage("explore", viewerId).postIds(), "차단한 사용자의 글은 빠져야 한다")

        unrelate(viewerId, authorId, "block")

        assertEquals(listOf(publicId), feedPage("explore", viewerId).postIds(), "차단을 풀면 다시 나와야 한다")
    }

    @Test
    fun `이용 제한 작성자와 기간이 남은 임시 정지 작성자의 글이 나온다`() {
        val restricted = onboardedUser()
        val suspended = onboardedUser()
        val restrictedPost = createdPublicId(restricted)
        val suspendedPost = createdPublicId(suspended)
        tx.execute {
            em.find(User::class.java, restricted).restrict()
            em.find(User::class.java, suspended).suspendUntil(LocalDateTime.now().plusDays(3))
        }

        assertEquals(listOf(suspendedPost, restrictedPost), feedPage("explore", onboardedUser()).postIds(), "정지 상태 작성자의 글도 나와야 한다")
    }

    @Test
    fun `7일 안의 글이 20개 이상이면 7일 경계 밖의 글은 나오지 않는다`() {
        val authorId = onboardedUser()
        val recent = List(19) { createdPublicId(authorId) }
        val inside = createdPublicId(authorId)
        val outside = createdPublicId(authorId)
        backdatePost(inside, LocalDateTime.now().minusDays(7).plusMinutes(10))
        backdatePost(outside, LocalDateTime.now().minusDays(7).minusMinutes(10))

        assertEquals(recent.reversed() + inside, feedPage("explore", size = 30).postIds(), "7일 안의 글 20개만 나와야 한다")
    }

    @Test
    fun `7일 안의 글이 20개에 못 미치면 7일이 지난 글을 최신순으로 이어서 준다`() {
        val authorId = onboardedUser()
        val old = List(2) { createdPublicId(authorId) }
        backdatePost(old[0], LocalDateTime.now().minusDays(9))
        backdatePost(old[1], LocalDateTime.now().minusDays(8))
        val recent = List(3) { createdPublicId(authorId) }

        // 크기 2로 내려 7일 경계를 둘째 페이지에서 넘는다
        val first = feedPage("explore", size = 2)
        val second = feedPage("explore", cursor = first["nextCursor"] as String, size = 2)
        val third = feedPage("explore", cursor = second["nextCursor"] as String, size = 2)

        assertEquals(
            recent.reversed() + old.reversed(),
            first.postIds() + second.postIds() + third.postIds(),
            "7일 안의 글 셋 뒤에 7일이 지난 글 둘이 최신순으로 이어져야 한다",
        )
        assertEquals(false, third["hasNext"], "더 부를 글이 없어야 한다")
        assertNull(third["nextCursor"], "다음 커서가 없어야 한다")
    }

    @Test
    fun `보여줄 글이 없으면 빈 목록이다`() {
        val page = feedPage("explore", onboardedUser())

        assertEquals(emptyList(), page.postIds(), "글이 없으면 빈 목록이어야 한다")
        assertEquals(false, page["hasNext"], "빈 목록은 다음 페이지가 없어야 한다")
    }

    @Test
    fun `리포스트는 탐색에 나오지 않고 원글이 게시 시각 자리에 한 번만 나온다`() {
        val authorId = onboardedUser()
        val older = createdPublicId(authorId)
        backdatePost(older, LocalDateTime.now().minusHours(2))
        val newer = createdPublicId(authorId)
        repost(older, onboardedUser()).andExpect(status().isCreated)

        assertEquals(listOf(newer, older), feedPage("explore").postIds(), "리포스트가 원글 순서를 바꾸거나 항목을 늘리면 안 된다")
    }

    @Test
    fun `기준 시각이 1시간 전인 커서는 그사이 7일이 지난 글을 그대로 준다`() {
        val authorId = onboardedUser()
        val now = LocalDateTime.now()
        val aging = createdPublicId(authorId)
        backdatePost(aging, now.minusDays(7).minusMinutes(30))
        // 7일 안의 글이 20개라 기간을 넓히지 않는다. 같은 시각이면 id가 작은 첫 글이 마지막이다
        val recent = List(20) { createdPublicId(authorId).also { backdatePost(it, now.minusHours(2)) } }
        assertTrue(aging !in feedPage("explore", size = 30).postIds(), "지금 기준으로는 7일이 지난 글이 없어야 한다")

        val page = feedPage("explore", cursor = SnsFeedCursorCodec.encodeExplore(now.minusHours(1), recent.first()), size = 30)

        assertEquals(listOf(aging), page.postIds(), "커서의 기준 시각으로 7일을 세야 한다")
    }

    @Test
    fun `첫 페이지 뒤에 쓴 글은 다음 페이지에 들어오지 않는다`() {
        val authorId = onboardedUser()
        val older = createdPublicId(authorId)
        val newer = createdPublicId(authorId)
        val first = feedPage("explore", size = 1)
        assertEquals(listOf(newer), first.postIds(), "첫 페이지는 가장 최근 글이어야 한다")

        createdPublicId(authorId)
        val second = feedPage("explore", cursor = first["nextCursor"] as String, size = 1)

        assertEquals(listOf(older), second.postIds(), "첫 페이지 뒤에 쓴 글이 끼어들면 안 된다")
        assertEquals(false, second["hasNext"], "기준 시각 뒤의 글로 다음 페이지가 생기면 안 된다")
    }

    @Test
    fun `크기 1로 끝까지 넘기면 같은 시각의 글까지 중복과 누락이 없고 마지막 페이지에서 멈춘다`() {
        val authorId = onboardedUser()
        val posts = List(5) { createdPublicId(authorId) }
        // 같은 시각이면 id가 큰 쪽이 먼저다
        backdatePost(posts[1], postCreatedAt(posts[2]))

        val seen = mutableListOf<String>()
        var cursor: String? = null
        var pages = 0
        do {
            val page = feedPage("explore", cursor = cursor, size = 1)
            seen += page.postIds()
            cursor = page["nextCursor"] as String?
            assertEquals(cursor != null, page["hasNext"], "hasNext와 nextCursor가 맞아야 한다")
            pages++
        } while (cursor != null && pages < 10)

        assertEquals(posts.reversed(), seen, "모든 글이 최신순으로 한 번씩 나와야 한다")
        assertEquals(5, pages, "글 수만큼의 페이지에서 멈춰야 한다")
    }

    @Test
    fun `앞 페이지의 마지막 글을 지워도 다음 페이지를 이어서 받는다`() {
        val authorId = onboardedUser()
        val (oldest, middle, newest) = List(3) { createdPublicId(authorId) }
        val first = feedPage("explore", size = 2)
        assertEquals(listOf(newest, middle), first.postIds(), "첫 페이지는 최근 글 둘이어야 한다")

        deletePost(middle, authorId).andExpect(status().isNoContent)

        assertEquals(listOf(oldest), feedPage("explore", cursor = first["nextCursor"] as String, size = 2).postIds(), "지운 글 뒤의 글이 이어져야 한다")
    }

    @Test
    fun `카드는 같은 보는 사람이 본 상세와 필드와 값이 같다`() {
        val viewerId = onboardedUser()
        val petAtPost = onboardedUser()
        defaultPet(petAtPost, breedLabel = "골든리트리버", birthYearMonth = "2021-03")
        val petAtPostId = createdPublicId(petAtPost, postBody(hashtags = listOf("산책", "골댕이")))
        val changedDefault = onboardedUser()
        defaultPet(changedDefault, breedLabel = "푸들")
        val changedDefaultId = createdPublicId(changedDefault)
        val secondPet = defaultPet(changedDefault, breedLabel = "말티즈", isDefault = false)
        jdbcTemplate.update("update pet_profiles set is_default = (id = ?) where user_id = ?", secondPet, changedDefault)
        val deletedPet = onboardedUser()
        val deletedPetPet = defaultPet(deletedPet)
        val deletedPetId = createdPublicId(deletedPet)
        jdbcTemplate.update("update pet_profiles set status = 'DELETED' where id = ?", deletedPetPet)
        val fixedBirth = onboardedUser()
        val fixedBirthPet = defaultPet(fixedBirth, birthYearMonth = "2020-05")
        val fixedBirthId = createdPublicId(fixedBirth)
        jdbcTemplate.update("update pet_profiles set birth_year_month = '2020-06' where id = ?", fixedBirthPet)
        val noBirth = onboardedUser()
        defaultPet(noBirth, breedLabel = "시바견")
        val noBirthId = createdPublicId(noBirth)
        val noPet = onboardedUser()
        val photoKeys = List(3) { tmpPhotoKey(noPet) }
        val photosId = createdPublicId(noPet, postBody(photoKeys = photoKeys))
        val textId = createdPublicId(noPet, postBody(caption = "텍스트만 있는 글"))
        val editedId = createdPublicId(noPet)
        updatePost(editedId, noPet, updateBody(caption = "고친 캡션", hashtags = listOf("수정"))).andExpect(status().isNoContent)
        val videoKey = tmpVideoKey(noPet)
        val videoId = createdPublicId(noPet, postBody(caption = null, video = videoJson(videoKey, tmpPhotoKey(noPet))))
        repost(photosId, viewerId).andExpect(status().isCreated)

        val cards = feedPage("explore", viewerId).feedItems().associate { (it["post"] as Map<*, *>)["publicId"] to it["post"] as Map<*, *> }

        val expected = listOf(petAtPostId, changedDefaultId, deletedPetId, fixedBirthId, noBirthId, photosId, textId, editedId, videoId)
        assertEquals(expected.toSet(), cards.keys, "모든 글이 카드로 나와야 한다")
        expected.forEach { assertEquals(postDetail(it, viewerId), cards[it], "카드가 상세와 같아야 한다: $it") }
        assertEquals(mapOf("breedLabel" to "골든리트리버", "birthYearMonth" to "2021-03"), cards.getValue(petAtPostId)["pet"], "게시할 때의 펫이어야 한다")
        assertEquals(listOf("산책", "골댕이"), cards.getValue(petAtPostId)["hashtags"], "해시태그는 단 순서여야 한다")
        assertEquals("푸들", (cards.getValue(changedDefaultId)["pet"] as Map<*, *>)["breedLabel"], "대표 펫을 바꿔도 게시할 때의 펫이어야 한다")
        assertNull(cards.getValue(deletedPetId)["pet"], "지운 펫은 태그가 없어야 한다")
        assertEquals("2020-06", (cards.getValue(fixedBirthId)["pet"] as Map<*, *>)["birthYearMonth"], "고친 생년월이 보여야 한다")
        assertEquals(mapOf("breedLabel" to "시바견", "birthYearMonth" to null), cards.getValue(noBirthId)["pet"], "생년월이 없으면 null이어야 한다")
        assertNull(cards.getValue(textId)["pet"], "펫이 없는 작성자는 태그가 없어야 한다")
        assertEquals(
            photoKeys.map { "https://fake-cdn.local/${promotedKeyOf(it)}" },
            cards.getValue(photosId)["photoUrls"],
            "사진은 단 순서여야 한다",
        )
        assertEquals(1, cards.getValue(photosId)["repostCount"], "리포스트 수가 실려야 한다")
        assertEquals(true, cards.getValue(photosId)["reposted"], "내가 리포스트한 글은 reposted가 true여야 한다")
        assertEquals(emptyList<Any>(), cards.getValue(textId)["photoUrls"], "텍스트 글은 사진이 없어야 한다")
        assertNull(cards.getValue(textId)["video"], "텍스트 글은 영상이 없어야 한다")
        assertEquals(true, cards.getValue(editedId)["edited"], "수정한 글은 edited가 true여야 한다")
        assertEquals("고친 캡션", cards.getValue(editedId)["caption"], "수정한 캡션이어야 한다")
        assertEquals(emptyList<Any>(), cards.getValue(videoId)["photoUrls"], "영상 글은 사진이 비어야 한다")
        assertEquals(
            "https://fake-cdn.local/${promotedKeyOf(videoKey)}",
            (cards.getValue(videoId)["video"] as Map<*, *>)["url"],
            "영상이 채워져야 한다",
        )
    }

    @Test
    fun `크기가 범위 밖이면 90001이고 다른 목록의 커서와 찾을 수 없는 게시물의 커서는 90002다`() {
        val viewerId = onboardedUser()

        expectError(getFeed("explore", viewerId, size = 0), 90001)
        expectError(getFeed("explore", viewerId, size = 101), 90001)
        expectError(
            getFeed("explore", viewerId, cursor = FollowCursorCodec.encode(SnsFollowDirection.FOLLOWERS, publicIdOf(viewerId), null, 1L)),
            90002,
        )
        expectError(
            getFeed("explore", viewerId, cursor = SnsFeedCursorCodec.encodeExplore(LocalDateTime.now(), "01JF9W3Q6R8T2V4X7Z9B1C3D5J")),
            90002,
        )
    }

    @Test
    fun `20개 페이지와 5개 페이지의 쿼리 수가 같다`() {
        val viewerId = onboardedUser()
        val authors = List(5) { onboardedUser().also { defaultPet(it) } }
        repeat(25) {
            val authorId = authors[it % authors.size]
            createdPublicId(authorId, postBody(photoKeys = listOf(tmpPhotoKey(authorId)), hashtags = listOf("산책")))
        }
        val statistics = em.entityManagerFactory.unwrap(SessionFactory::class.java).statistics
        statistics.isStatisticsEnabled = true

        statistics.clear()
        assertEquals(20, feedPage("explore", viewerId, size = 20).postIds().size, "20개 페이지여야 한다")
        val twenty = statistics.prepareStatementCount
        statistics.clear()
        assertEquals(5, feedPage("explore", viewerId, size = 5).postIds().size, "5개 페이지여야 한다")
        val five = statistics.prepareStatementCount

        assertNotNull(twenty.takeIf { it > 0 }, "쿼리 수를 셌어야 한다")
        assertEquals(five, twenty, "카드 조립의 쿼리 수가 게시물 수와 상관없어야 한다")
    }

    private fun expectError(
        result: ResultActions,
        errorCode: Int,
    ) {
        result
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(errorCode))
    }
}
