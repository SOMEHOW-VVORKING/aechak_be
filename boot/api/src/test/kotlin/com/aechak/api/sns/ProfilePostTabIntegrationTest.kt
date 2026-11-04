package com.aechak.api.sns

import com.aechak.domain.user.user.User
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 통합: 프로필 그리드 탭이 주인의 지우지 않은 원글을, 영상 탭이 그중 영상 원글만 최신순 칸으로 주고 리포스트 탭이 주인이 리포스트한
 * 원글을 최근 리포스트순으로 주는지, 세 탭이 팔로워 목록과 같은 주인 판정을 하고 칸의 썸네일, 사진 수, 텍스트 미리보기가 게시물 종류에 맞는지.
 * 깨지면 차단 관계인 사용자의 글이 프로필에 보이거나 지운 글과 리포스트가 그리드에 섞이거나 영상 탭에 다른 글이 나오거나
 * 리포스트 탭에 보는 사람이 숨긴 작성자의 글이 나오거나 텍스트 칸의 글자가 반으로 잘린다.
 */
class ProfilePostTabIntegrationTest : SnsPostIntegrationTestBase() {
    @Test
    fun `비로그인도 그리드에서 사진, 영상, 텍스트 원글을 모두 최신순으로 받는다`() {
        val ownerId = onboardedUser()
        val photo = createdPublicId(ownerId, postBody(photoKeys = listOf(tmpPhotoKey(ownerId))))
        val video = createdPublicId(ownerId, postBody(caption = null, video = videoJson(tmpVideoKey(ownerId), tmpPhotoKey(ownerId))))
        val text = createdPublicId(ownerId, postBody(caption = "텍스트 글"))

        val page = tabPage("posts", ownerId)

        assertEquals(listOf(text, video, photo), page.cellIds(), "모든 원글이 최신순이어야 한다")
        assertEquals(setOf("items", "nextCursor", "hasNext"), page.keys, "탭 응답에는 totalCount가 없어야 한다")
    }

    @Test
    fun `칸은 사진 게시물의 첫 사진과 장수, 영상 게시물의 썸네일, 텍스트 게시물의 캡션 앞 100 코드 포인트를 싣는다`() {
        val ownerId = onboardedUser()
        val photoKeys = List(3) { tmpPhotoKey(ownerId) }
        val photo = createdPublicId(ownerId, postBody(caption = "사진 글", photoKeys = photoKeys))
        val thumbnailKey = tmpPhotoKey(ownerId)
        val video = createdPublicId(ownerId, postBody(caption = "영상 글", video = videoJson(tmpVideoKey(ownerId), thumbnailKey)))
        // 이모지는 UTF-16 두 칸이라 글자 수로 자르면 100번째 코드 포인트가 반으로 잘린다
        val long = createdPublicId(ownerId, postBody(caption = "가".repeat(99) + "🐶" + "끝"))
        val exact = createdPublicId(ownerId, postBody(caption = "나".repeat(100)))

        val cells = tabPage("posts", ownerId, viewerId = onboardedUser()).feedItems().associateBy { it["publicId"] }

        assertEquals(
            mapOf(
                "publicId" to photo,
                "mediaType" to "PHOTO",
                "thumbnailUrl" to "https://fake-cdn.local/${promotedKeyOf(photoKeys.first())}",
                "photoCount" to 3,
                "captionPreview" to null,
            ),
            cells[photo],
            "사진 칸은 첫 사진과 장수만 싣고 미리보기가 없어야 한다",
        )
        assertEquals(
            mapOf(
                "publicId" to video,
                "mediaType" to "VIDEO",
                "thumbnailUrl" to "https://fake-cdn.local/${promotedKeyOf(thumbnailKey)}",
                "photoCount" to 0,
                "captionPreview" to null,
            ),
            cells[video],
            "영상 칸은 썸네일만 싣고 사진 수가 0이어야 한다",
        )
        assertEquals(
            mapOf(
                "publicId" to long,
                "mediaType" to "TEXT",
                "thumbnailUrl" to null,
                "photoCount" to 0,
                "captionPreview" to "가".repeat(99) + "🐶",
            ),
            cells[long],
            "텍스트 칸은 캡션 앞 100 코드 포인트를 싣고 썸네일이 없어야 한다",
        )
        assertEquals("나".repeat(100), cells.getValue(exact)["captionPreview"], "100 코드 포인트 캡션은 자르지 않아야 한다")
    }

    @Test
    fun `리포스트는 그리드에 나오지 않고 프로필 게시물 수를 바꾸지 않는다`() {
        val ownerId = onboardedUser()
        val own = createdPublicId(ownerId)
        repost(createdPublicId(onboardedUser()), ownerId).andExpect(status().isCreated)

        assertEquals(listOf(own), tabPage("posts", ownerId).cellIds(), "그리드에는 주인의 원글만 나와야 한다")
        mockMvc
            .perform(get("/api/v1/sns/users/${publicIdOf(ownerId)}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.postCount").value(1))
    }

    @Test
    fun `주인이 나를 차단하면 404 130000이고 내가 주인을 차단하면 403 130001이다`() {
        val viewerId = onboardedUser()
        val blockingMe = onboardedUser().also { createdPublicId(it) }
        val blockedByMe = onboardedUser().also { createdPublicId(it) }
        relate(blockingMe, viewerId, "block")
        relate(viewerId, blockedByMe, "block")

        TABS.forEach { tab ->
            getTab(tab, publicIdOf(blockingMe), viewerId)
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.errorCode").value(130000))
            getTab(tab, publicIdOf(blockedByMe), viewerId)
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.errorCode").value(130001))
        }
    }

    @Test
    fun `없는 주인은 404 130000이다`() {
        TABS.forEach { tab ->
            getTab(tab, "01JF9W3Q6R8T2V4X7Z9B1C3D5J", onboardedUser())
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.errorCode").value(130000))
        }
    }

    @Test
    fun `영상 탭은 영상 원글만 최신순으로 주고 커서로 이어진다`() {
        val ownerId = onboardedUser()
        val older = videoPost(ownerId)
        createdPublicId(ownerId, postBody(photoKeys = listOf(tmpPhotoKey(ownerId))))
        createdPublicId(ownerId, postBody(caption = "텍스트 글"))
        val deleted = videoPost(ownerId)
        deletePost(deleted, ownerId).andExpect(status().isNoContent)
        val newer = videoPost(ownerId, caption = "영상 글")

        val first = tabPage("videos", ownerId, size = 1)
        val second = tabPage("videos", ownerId, cursor = first["nextCursor"] as String, size = 1)

        assertEquals(listOf(newer), first.cellIds(), "첫 페이지는 가장 최근 영상 글이어야 한다")
        assertEquals("VIDEO", first.feedItems().single()["mediaType"], "영상 칸이어야 한다")
        assertEquals(listOf(older), second.cellIds(), "사진, 텍스트, 지운 글을 건너뛰고 다음 영상 글이 이어져야 한다")
        assertEquals(false, second["hasNext"], "마지막 페이지여야 한다")
    }

    @Test
    fun `리포스트 탭은 주인이 리포스트한 원글을 최근 리포스트순 칸으로 준다`() {
        val ownerId = onboardedUser()
        val authorId = onboardedUser()
        val first = createdPublicId(authorId, postBody(photoKeys = listOf(tmpPhotoKey(authorId))))
        val second = videoPost(authorId)
        val third = createdPublicId(authorId, postBody(caption = "텍스트 글"))
        createdPublicId(ownerId)
        // 게시 순서와 다르게 리포스트한다
        listOf(second, third, first).forEach { repost(it, ownerId).andExpect(status().isCreated) }

        val page = tabPage("reposts", ownerId)

        assertEquals(listOf(first, third, second), page.cellIds(), "주인의 원글 없이 원글의 공개 ID가 최근 리포스트순이어야 한다")
        assertEquals(listOf("PHOTO", "TEXT", "VIDEO"), page.feedItems().map { it["mediaType"] }, "칸은 원글 기준이어야 한다")
    }

    @Test
    fun `뮤트한 주인의 리포스트는 나오고 그 안에서 내가 뮤트한 다른 작성자의 원글은 빠진다`() {
        val viewerId = onboardedUser()
        val ownerId = onboardedUser()
        val mutedAuthor = onboardedUser()
        val visible = createdPublicId(onboardedUser())
        val mutedPost = createdPublicId(mutedAuthor)
        listOf(visible, mutedPost).forEach { repost(it, ownerId).andExpect(status().isCreated) }
        relate(viewerId, ownerId, "mute")
        relate(viewerId, mutedAuthor, "mute")

        assertEquals(listOf(visible), tabPage("reposts", ownerId, viewerId).cellIds(), "주인 뮤트는 거르지 않고 원글 작성자 뮤트만 걸러야 한다")
    }

    @Test
    fun `주인 본인은 자기가 뮤트한 작성자의 원글도 리포스트 탭에서 본다`() {
        val ownerId = onboardedUser()
        val mutedAuthor = onboardedUser()
        val publicId = createdPublicId(mutedAuthor)
        relate(ownerId, mutedAuthor, "mute")
        repost(publicId, ownerId).andExpect(status().isCreated)

        assertEquals(listOf(publicId), tabPage("reposts", ownerId, viewerId = ownerId).cellIds(), "주인 본인에게는 뮤트를 거르지 않아야 한다")
    }

    @Test
    fun `보는 사람과 원글 작성자가 어느 쪽으로든 차단 관계면 그 원글이 빠진다`() {
        val viewerId = onboardedUser()
        val ownerId = onboardedUser()
        val blockedByMe = onboardedUser()
        val blockingMe = onboardedUser()
        val visible = createdPublicId(onboardedUser())
        val blockedPost = createdPublicId(blockedByMe)
        val blockingPost = createdPublicId(blockingMe)
        listOf(visible, blockedPost, blockingPost).forEach { repost(it, ownerId).andExpect(status().isCreated) }
        relate(viewerId, blockedByMe, "block")
        relate(blockingMe, viewerId, "block")

        assertEquals(listOf(visible), tabPage("reposts", ownerId, viewerId).cellIds(), "차단 관계인 작성자의 원글은 빠져야 한다")
        assertEquals(listOf(blockingPost, blockedPost, visible), tabPage("reposts", ownerId).cellIds(), "비로그인은 모두 봐야 한다")
    }

    @Test
    fun `지운 원글과 탈퇴한 작성자의 원글은 리포스트 탭에서 빠진다`() {
        val ownerId = onboardedUser()
        val authorId = onboardedUser()
        val withdrawn = onboardedUser()
        val deleted = createdPublicId(authorId)
        val withdrawnPost = createdPublicId(withdrawn)
        val visible = createdPublicId(authorId)
        listOf(deleted, withdrawnPost, visible).forEach { repost(it, ownerId).andExpect(status().isCreated) }
        deletePost(deleted, authorId).andExpect(status().isNoContent)
        tx.execute { em.find(User::class.java, withdrawn).withdraw() }

        assertEquals(listOf(visible), tabPage("reposts", ownerId, onboardedUser()).cellIds(), "볼 수 없는 원글은 빠져야 한다")
    }

    @Test
    fun `첫 페이지 뒤에 취소한 리포스트는 다음 페이지에서 빠지고 본 칸이 다시 나오지 않는다`() {
        val ownerId = onboardedUser()
        val authorId = onboardedUser()
        val posts = List(4) { createdPublicId(authorId) }
        posts.forEach { repost(it, ownerId).andExpect(status().isCreated) }
        val first = tabPage("reposts", ownerId, size = 2)
        assertEquals(listOf(posts[3], posts[2]), first.cellIds(), "첫 페이지는 최근 리포스트 둘이어야 한다")

        // 이미 본 마지막 칸과 아직 보지 않은 칸의 리포스트를 함께 취소한다
        unrepost(posts[2], ownerId).andExpect(status().isNoContent)
        unrepost(posts[1], ownerId).andExpect(status().isNoContent)
        val second = tabPage("reposts", ownerId, cursor = first["nextCursor"] as String, size = 2)

        assertEquals(listOf(posts[0]), second.cellIds(), "취소한 리포스트는 빠지고 남은 리포스트가 이어져야 한다")
        assertEquals(false, second["hasNext"], "마지막 페이지여야 한다")
    }

    @Test
    fun `리포스트가 없는 주인의 리포스트 탭은 빈 목록이다`() {
        val ownerId = onboardedUser()
        createdPublicId(ownerId)

        val page = tabPage("reposts", ownerId, viewerId = onboardedUser())

        assertEquals(emptyList(), page.cellIds(), "리포스트가 없으면 빈 목록이어야 한다")
        assertEquals(false, page["hasNext"], "빈 목록은 다음 페이지가 없어야 한다")
    }

    @Test
    fun `그리드 커서를 영상 탭에 보내면 90002다`() {
        val ownerId = onboardedUser()
        repeat(2) { videoPost(ownerId) }
        val cursor = tabPage("posts", ownerId, size = 1)["nextCursor"] as String

        getTab("videos", publicIdOf(ownerId), cursor = cursor)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90002))
    }

    @Test
    fun `뮤트한 주인의 글도 그대로 나온다`() {
        val viewerId = onboardedUser()
        val ownerId = onboardedUser()
        val publicId = createdPublicId(ownerId)
        relate(viewerId, ownerId, "mute")

        assertEquals(listOf(publicId), tabPage("posts", ownerId, viewerId).cellIds(), "프로필은 뮤트를 거르지 않아야 한다")
    }

    @Test
    fun `지운 글과 운영자가 지운 글은 주인 본인의 그리드에서도 빠진다`() {
        val ownerId = onboardedUser()
        val deleted = createdPublicId(ownerId)
        deletePost(deleted, ownerId).andExpect(status().isNoContent)
        val operatorDeleted = createdPublicId(ownerId)
        jdbcTemplate.update("update sns_posts set status = 'DELETED', deletion_reason = 'OPERATOR' where public_id = ?", operatorDeleted)
        val visible = createdPublicId(ownerId)

        assertEquals(listOf(visible), tabPage("posts", ownerId, viewerId = ownerId).cellIds(), "지운 글은 주인에게도 나오지 않아야 한다")
    }

    @Test
    fun `이용 제한 주인과 기간이 남은 임시 정지 주인의 글이 나온다`() {
        val restricted = onboardedUser()
        val suspended = onboardedUser()
        val restrictedPost = createdPublicId(restricted)
        val suspendedPost = createdPublicId(suspended)
        tx.execute {
            em.find(User::class.java, restricted).restrict()
            em.find(User::class.java, suspended).suspendUntil(LocalDateTime.now().plusDays(3))
        }
        val viewerId = onboardedUser()

        assertEquals(listOf(restrictedPost), tabPage("posts", restricted, viewerId).cellIds(), "이용 제한 주인의 글이 나와야 한다")
        assertEquals(listOf(suspendedPost), tabPage("posts", suspended, viewerId).cellIds(), "임시 정지 주인의 글이 나와야 한다")
    }

    @Test
    fun `글이 없는 주인의 그리드는 빈 목록이다`() {
        val page = tabPage("posts", onboardedUser(), viewerId = onboardedUser())

        assertEquals(emptyList(), page.cellIds(), "글이 없으면 빈 목록이어야 한다")
        assertEquals(false, page["hasNext"], "빈 목록은 다음 페이지가 없어야 한다")
        assertNull(page["nextCursor"], "빈 목록은 다음 커서가 없어야 한다")
    }

    @Test
    fun `다른 주인의 그리드 커서는 90002다`() {
        val ownerId = onboardedUser()
        val otherId = onboardedUser().also { createdPublicId(it) }
        repeat(2) { createdPublicId(ownerId) }
        val cursor = tabPage("posts", ownerId, size = 1)["nextCursor"] as String

        getTab("posts", publicIdOf(otherId), cursor = cursor)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90002))
    }

    @Test
    fun `마지막 칸의 글을 지워도 다음 페이지를 이어서 받는다`() {
        val ownerId = onboardedUser()
        val (oldest, middle, newest) = List(3) { createdPublicId(ownerId) }
        val first = tabPage("posts", ownerId, size = 2)
        assertEquals(listOf(newest, middle), first.cellIds(), "첫 페이지는 최근 글 둘이어야 한다")

        deletePost(middle, ownerId).andExpect(status().isNoContent)
        val second = tabPage("posts", ownerId, cursor = first["nextCursor"] as String, size = 2)

        assertEquals(listOf(oldest), second.cellIds(), "지운 글 뒤의 글이 이어져야 한다")
        assertEquals(false, second["hasNext"], "마지막 페이지여야 한다")
    }

    /** 영상 게시물을 게시하고 publicId를 돌려준다. */
    private fun videoPost(
        ownerId: Long,
        caption: String? = null,
    ): String = createdPublicId(ownerId, postBody(caption = caption, video = videoJson(tmpVideoKey(ownerId), tmpPhotoKey(ownerId))))

    /** 프로필 탭(posts, videos, reposts)을 부른다. */
    private fun getTab(
        tab: String,
        ownerPublicId: String,
        viewerId: Long? = null,
        cursor: String? = null,
        size: Int? = null,
    ): ResultActions {
        val request = get("/api/v1/sns/users/$ownerPublicId/$tab")
        cursor?.let { request.param("cursor", it) }
        size?.let { request.param("size", it.toString()) }
        if (viewerId != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(viewerId)}")
        return mockMvc.perform(request)
    }

    /** 프로필 탭 한 페이지를 200으로 받고 data 객체를 돌려준다. */
    private fun tabPage(
        tab: String,
        ownerId: Long,
        viewerId: Long? = null,
        cursor: String? = null,
        size: Int? = null,
    ): Map<String, Any?> {
        val json =
            getTab(tab, publicIdOf(ownerId), viewerId, cursor, size)
                .andExpect(status().isOk)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)
        return JsonPath.read(json, "$.data")
    }

    /** 탭 페이지 칸의 게시물 publicId를 순서대로 돌려준다. */
    private fun Map<String, Any?>.cellIds(): List<String> = feedItems().map { it["publicId"] as String }

    companion object {
        // 주인 판정이 같은 탭
        private val TABS = listOf("posts", "videos", "reposts")
    }
}
