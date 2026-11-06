package com.aechak.api.sns

import com.aechak.domain.user.user.User
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 통합: 계정 세그먼트가 닉네임 같음, 시작, 포함 순서와 팔로워 수로 사용자를 주고 내가 차단만 한 사용자를 빼며 나를 차단한 사용자는
 * 기본 프로필로 주는지, 해시태그 세그먼트가 부분 일치 태그를 게시글 수 순서로 주는지, 두 세그먼트가 공백을 뺀 검색어로 찾고 이어 읽는지.
 * 깨지면 계정 순서가 화면과 다르거나 차단 관계가 드러나는 대신 숨겨야 할 정보가 나오거나 공백이 든 검색어로 아무것도 찾지 못한다.
 */
class SnsSearchSegmentIntegrationTest : SnsPostIntegrationTestBase() {
    @Test
    fun `계정은 닉네임이 같은 계정, 시작하는 계정, 나머지 순이고 같은 묶음 안에서는 팔로워 수가 많은 순이다`() {
        val exact = userNamed("산책", followers = 10)
        val starts = userNamed("산책메이트", followers = 3_120)
        val bigContains = userNamed("한강산책클럽", followers = 8_940)
        val smallContains = userNamed("댕댕산책일기", followers = 1_260)
        userNamed("보리언니", followers = 9_999)

        val page = userPage("산책")

        assertEquals(listOf(exact, starts, bigContains, smallContains).map(::publicIdOf), page.userIds())
        assertEquals(10, (page.items().first()["followerCount"] as Number).toInt(), "팔로워 수가 실려야 한다")
    }

    @Test
    fun `공백이 든 검색어는 공백을 빼고 닉네임과 태그 이름을 찾는다`() {
        val club = userNamed("한강산책클럽")
        createdPublicId(onboardedUser(), postBody(hashtags = listOf("한강산책")))

        assertEquals(listOf(publicIdOf(club)), userPage("한강 산책").userIds())
        assertEquals(listOf("한강산책"), hashtagPage("한강 산책").items().map { it["name"] })
    }

    @Test
    fun `내가 차단만 한 사용자와 탈퇴한 사용자는 빠지고 나를 차단한 사용자는 기본 프로필로 나온다`() {
        val viewerId = userNamed("보리집사")
        val blockedByMe = userNamed("보리01")
        val blockingMe = userNamed("보리02", followers = 5)
        val mutual = userNamed("보리03")
        val muted = userNamed("보리04")
        val followed = userNamed("보리05")
        val withdrawn = userNamed("보리06")
        defaultPet(blockingMe)
        relate(viewerId, blockedByMe, "block")
        relate(blockingMe, viewerId, "block")
        relate(viewerId, mutual, "block")
        relate(mutual, viewerId, "block")
        relate(viewerId, muted, "mute")
        relate(viewerId, followed, "follow")
        tx.execute { em.find(User::class.java, withdrawn).withdraw() }

        val rows = userPage("보리", viewerId).items().associateBy { it["publicId"] }

        assertEquals(
            setOf(viewerId, blockingMe, mutual, muted, followed).map(::publicIdOf).toSet(),
            rows.keys,
            "내가 차단만 한 사용자와 탈퇴한 사용자만 빠져야 한다",
        )
        val blocking = rows.getValue(publicIdOf(blockingMe))
        assertEquals(true, blocking["blockingMe"])
        assertEquals(
            listOf(null, null, null, false),
            listOf(blocking["profileImageUrl"], blocking["petSummary"], blocking["followerCount"], blocking["following"]),
        )
        assertEquals(true, rows.getValue(publicIdOf(mutual))["blockingMe"], "서로 차단이면 기본 프로필로 나와야 한다")
        assertEquals(true, rows.getValue(publicIdOf(followed))["following"])
        assertEquals(true, rows.getValue(publicIdOf(viewerId))["isMe"])
        assertEquals(false, rows.getValue(publicIdOf(muted))["blockingMe"], "뮤트한 사용자는 그대로 나와야 한다")
    }

    @Test
    fun `비로그인은 차단 관계 없이 탈퇴한 사용자만 빠진다`() {
        val a = userNamed("코코아빠")
        val b = userNamed("코코엄마")
        relate(a, b, "block")
        defaultPet(b, breedLabel = "말티즈")
        defaultPet(b, isDefault = false)

        val rows = userPage("코코").items().associateBy { it["publicId"] }

        assertEquals(setOf(publicIdOf(a), publicIdOf(b)), rows.keys)
        assertEquals(mapOf("breedLabel" to "말티즈", "activePetCount" to 2), rows.getValue(publicIdOf(b))["petSummary"], "대표 펫과 펫 수가 실려야 한다")
        assertTrue(rows.values.all { it["blockingMe"] == false && it["isMe"] == false })
    }

    @Test
    fun `계정은 묶음과 팔로워 수 경계를 넘어 겹침 없이 이어 읽는다`() {
        val users =
            listOf(
                userNamed("두부", followers = 1),
                userNamed("두부맘", followers = 7),
                userNamed("두부아빠", followers = 7),
                userNamed("우리두부", followers = 3),
                userNamed("작은두부", followers = 3),
            )
        val first = userPage("두부", size = 2)
        val second = userPage("두부", cursor = first["nextCursor"] as String, size = 2)
        val third = userPage("두부", cursor = second["nextCursor"] as String, size = 2)

        assertEquals(users.map(::publicIdOf).toSet(), (first.userIds() + second.userIds() + third.userIds()).toSet())
        assertEquals(5, (first.userIds() + second.userIds() + third.userIds()).size, "겹치는 행이 없어야 한다")
        assertEquals(false, third["hasNext"])
        users(
            keyword = "두부부",
            cursor = first["nextCursor"] as String,
        ).andExpect(status().isBadRequest).andExpect(jsonPath("$.errorCode").value(90002))
    }

    @Test
    fun `12자를 넘는 닉네임 검색어는 빈 목록이다`() {
        userNamed("가나다라마바사아자차카타")

        assertTrue(userPage("가나다라마바사아자차카타파").items().isEmpty())
    }

    @Test
    fun `해시태그는 이름에 검색어가 든 태그를 게시글 수 순서로 주고 글이 모두 지워진 태그와 캡션에만 쓴 태그는 빠진다`() {
        val authorId = onboardedUser()
        repeat(3) { createdPublicId(authorId, postBody(hashtags = listOf("봄산책"))) }
        repeat(2) { createdPublicId(authorId, postBody(hashtags = listOf("산책스타그램"))) }
        createdPublicId(authorId, postBody(hashtags = listOf("한강산책")))
        val gone = createdPublicId(authorId, postBody(hashtags = listOf("산책월드컵")))
        deletePost(gone, authorId).andExpect(status().isNoContent)
        createdPublicId(authorId, postBody(caption = "#산책일기"))

        val first = hashtagPage("#산책", size = 2)
        val second = hashtagPage("#산책", cursor = first["nextCursor"] as String, size = 2)

        assertEquals(
            listOf(
                mapOf("name" to "봄산책", "postCount" to 3),
                mapOf("name" to "산책스타그램", "postCount" to 2),
                mapOf(
                    "name" to "한강산책",
                    "postCount" to 1,
                ),
            ),
            first.items() + second.items(),
        )
        assertEquals(false, second["hasNext"])
    }

    private fun userNamed(
        nickname: String,
        followers: Int = 0,
    ): Long {
        val userId = onboardedUser()
        jdbcTemplate.update("update user_profiles set nickname = ? where user_id = ?", nickname, userId)
        jdbcTemplate.update(
            "insert into sns_user_stats (user_id, follower_count, following_count, post_count, created_at, updated_at) " +
                "values (?, ?, 0, 0, now(6), now(6)) on duplicate key update follower_count = values(follower_count)",
            userId,
            followers,
        )
        return userId
    }

    private fun users(
        keyword: String,
        viewerId: Long? = null,
        cursor: String? = null,
        size: Int? = null,
    ): ResultActions = segment("users", keyword, viewerId, cursor, size)

    private fun userPage(
        keyword: String,
        viewerId: Long? = null,
        cursor: String? = null,
        size: Int? = null,
    ): Map<String, Any?> = data(users(keyword, viewerId, cursor, size))

    private fun hashtagPage(
        keyword: String,
        cursor: String? = null,
        size: Int? = null,
    ): Map<String, Any?> = data(segment("hashtags", keyword, null, cursor, size))

    private fun segment(
        kind: String,
        keyword: String,
        viewerId: Long?,
        cursor: String?,
        size: Int?,
    ): ResultActions {
        val request = get("/api/v1/sns/search/$kind").param("keyword", keyword)
        cursor?.let { request.param("cursor", it) }
        size?.let { request.param("size", it.toString()) }
        if (viewerId != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(viewerId)}")
        return mockMvc.perform(request)
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
    private fun Map<String, Any?>.items(): List<Map<String, Any?>> = this["items"] as List<Map<String, Any?>>

    private fun Map<String, Any?>.userIds(): List<String> = items().map { it["publicId"] as String }
}
