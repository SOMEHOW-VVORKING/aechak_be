package com.aechak.api.sns

import com.aechak.domain.sns.error.SnsErrorCode
import com.aechak.domain.user.user.enums.UserStatus
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** 영구 정지 사용자의 프로필과 게시물은 탈퇴한 사용자와 같은 오류 코드로 숨고 제재가 풀리면 다시 보인다 */
class BannedUserVisibilityIntegrationTest : SnsPostIntegrationTestBase() {
    @Test
    fun `영구 정지 작성자의 게시물은 피드와 검색에서 빠지고 상세와 반응은 404 130400이며 풀리면 다시 보인다`() {
        val viewer = onboardedUser()
        val banned = onboardedUser()
        val publicId = createdPublicId(banned, postBody(caption = "정지된 산책 기록"))
        setStatus(banned, UserStatus.BANNED)

        assertFalse(publicId in feedPage("explore", viewer).postIds())
        assertFalse(publicId in searchPosts("정지된", viewer).postIds())
        expectError(getPost(publicId, viewer), 404, SnsErrorCode.SNS_POST_UNAVAILABLE.code)
        expectError(like(publicId, viewer), 404, SnsErrorCode.SNS_POST_UNAVAILABLE.code)
        expectError(repost(publicId, viewer), 404, SnsErrorCode.SNS_POST_UNAVAILABLE.code)
        expectError(save(publicId, viewer), 404, SnsErrorCode.SNS_POST_UNAVAILABLE.code)
        expectError(getComments(publicId, viewer), 404, SnsErrorCode.SNS_POST_UNAVAILABLE.code)

        setStatus(banned, UserStatus.ACTIVE)

        assertTrue(publicId in feedPage("explore", viewer).postIds())
        getPost(publicId, viewer).andExpect(status().isOk)
    }

    @Test
    fun `팔로우한 영구 정지 사용자의 글과 리포스트는 팔로잉 피드에 나오지 않는다`() {
        val viewer = onboardedUser()
        val banned = onboardedUser()
        val own = createdPublicId(banned)
        val reposted = createdPublicId(onboardedUser())
        repost(reposted, banned).andExpect(status().is2xxSuccessful)
        relate(viewer, banned, "follow")
        setStatus(banned, UserStatus.BANNED)

        val ids = feedPage("following", viewer).postIds()

        assertFalse(own in ids, "영구 정지 사용자의 글이 빠져야 한다")
        assertFalse(reposted in ids, "영구 정지 사용자의 리포스트가 빠져야 한다")
    }

    @Test
    fun `영구 정지 사용자의 프로필과 탭과 팔로우는 404 130000이다`() {
        val viewer = onboardedUser()
        val banned = onboardedUser()
        val bannedPublicId = publicIdOf(banned)
        setStatus(banned, UserStatus.BANNED)

        expectError(userGet("/api/v1/sns/users/$bannedPublicId", viewer), 404, SnsErrorCode.SNS_USER_UNAVAILABLE.code)
        expectError(userGet("/api/v1/sns/users/$bannedPublicId/posts", viewer), 404, SnsErrorCode.SNS_USER_UNAVAILABLE.code)
        expectError(userGet("/api/v1/sns/users/$bannedPublicId/followers", viewer), 404, SnsErrorCode.SNS_USER_UNAVAILABLE.code)
        expectError(
            mockMvc.perform(
                post("/api/v1/sns/users/$bannedPublicId/follow")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(viewer)}"),
            ),
            404,
            SnsErrorCode.SNS_USER_UNAVAILABLE.code,
        )
    }

    @Test
    fun `영구 정지 사용자는 계정 검색, 팔로워 목록, 좋아요한 사람 목록에서 빠지고 좋아요 수에는 남는다`() {
        val viewer = onboardedUser()
        val owner = onboardedUser()
        val banned = onboardedUser()
        val kept = onboardedUser()
        jdbcTemplate.update("update user_profiles set nickname = ? where user_id = ?", "정지검색대상", banned)
        jdbcTemplate.update("update user_profiles set nickname = ? where user_id = ?", "정지검색남음", kept)
        val publicId = createdPublicId(owner)
        listOf(banned, kept).forEach {
            like(publicId, it).andExpect(status().isCreated)
            relate(it, owner, "follow")
        }
        setStatus(banned, UserStatus.BANNED)

        val users = data(userGet("/api/v1/sns/search/users?keyword=정지검색", viewer))
        val followers = data(userGet("/api/v1/sns/users/${publicIdOf(owner)}/followers", viewer))
        val likers = data(userGet("/api/v1/sns/posts/$publicId/likers", viewer))

        assertEquals(listOf(publicIdOf(kept)), users.itemIds())
        assertEquals(listOf(publicIdOf(kept)), followers.itemIds())
        assertEquals(listOf(publicIdOf(kept)), likers.itemIds())
        assertEquals(2, (likers["likeCount"] as Number).toInt())
    }

    @Test
    fun `영구 정지 사용자는 차단과 뮤트 관리 목록과 수, 댓글 좋아요한 사람 목록에서 빠진다`() {
        val viewer = onboardedUser()
        val banned = onboardedUser()
        val kept = onboardedUser()
        val postId = createdPublicId(onboardedUser())
        val comment = seedComment(onboardedUser(), postId)
        listOf(banned, kept).forEach {
            likeComment(comment, it).andExpect(status().is2xxSuccessful)
            relate(viewer, it, "block")
            relate(viewer, it, "mute")
        }
        setStatus(banned, UserStatus.BANNED)

        listOf("blocks", "mutes").forEach { kind ->
            val page = data(userGet("/api/v1/sns/me/$kind", viewer))
            assertEquals(listOf(publicIdOf(kept)), page.itemIds(), kind)
            assertEquals(1, (page["totalCount"] as Number).toInt(), kind)
        }
        assertEquals(listOf(publicIdOf(kept)), data(commentLikers(comment, onboardedUser())).itemIds())
    }

    private fun setStatus(
        userId: Long,
        status: UserStatus,
    ) {
        tx.execute {
            em
                .createQuery("update User u set u.status = :st, u.sanctionUntil = null where u.id = :id")
                .setParameter("st", status)
                .setParameter("id", userId)
                .executeUpdate()
        }
    }

    private fun searchPosts(
        keyword: String,
        viewerId: Long,
    ): Map<String, Any?> = data(userGet("/api/v1/sns/search/posts?keyword=$keyword&sort=LATEST", viewerId))

    private fun userGet(
        path: String,
        viewerId: Long,
    ): ResultActions = mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(viewerId)}"))

    private fun expectError(
        result: ResultActions,
        httpStatus: Int,
        errorCode: Int,
    ) {
        result.andExpect(status().`is`(httpStatus)).andExpect(jsonPath("$.errorCode").value(errorCode))
    }

    private fun data(result: ResultActions): Map<String, Any?> =
        JsonPath.read(
            result
                .andExpect(status().isOk)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8),
            "$.data",
        )

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.itemIds(): List<String> = (this["items"] as List<Map<String, Any?>>).map { it["publicId"] as String }
}
