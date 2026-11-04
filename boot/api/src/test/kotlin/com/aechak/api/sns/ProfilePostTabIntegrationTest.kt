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
 * 통합: 프로필 그리드 탭이 주인의 지우지 않은 원글을 최신순 칸으로 주고 팔로워 목록과 같은 주인 판정을 하는지,
 * 칸의 썸네일, 사진 수, 텍스트 미리보기가 게시물 종류에 맞는지.
 * 깨지면 차단 관계인 사용자의 글이 프로필에 보이거나 지운 글과 리포스트가 그리드에 섞이거나 텍스트 칸의 글자가 반으로 잘린다.
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

        getTab("posts", publicIdOf(blockingMe), viewerId)
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(130000))
        getTab("posts", publicIdOf(blockedByMe), viewerId)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(130001))
    }

    @Test
    fun `없는 주인은 404 130000이다`() {
        getTab("posts", "01JF9W3Q6R8T2V4X7Z9B1C3D5J", onboardedUser())
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(130000))
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

    /** 프로필 탭(posts)을 부른다. */
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
}
