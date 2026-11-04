package com.aechak.api.sns

import com.aechak.domain.user.user.User
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 통합: 저장됨 탭이 내가 저장한 글만 최근 저장순 칸으로 주고 지운 글, 탈퇴한 작성자의 글, 차단 관계이거나 뮤트한 작성자의 글을 빼며
 * 커서가 마지막 저장 행과 내 범위에 묶이는지.
 * 깨지면 볼 수 없는 글이나 남의 저장이 저장됨 탭에 나오거나 차단과 뮤트를 풀어도 저장한 글이 돌아오지 않거나 내리는 동안 겹치고 빠지거나
 * 다른 사람의 커서로 페이지를 넘긴다.
 */
class SavedTabIntegrationTest : SnsPostIntegrationTestBase() {
    @Test
    fun `내가 저장한 글을 최근 저장순 칸으로 주고 내 글도 나온다`() {
        val userId = onboardedUser()
        val authorId = onboardedUser()
        val photo = createdPublicId(authorId, postBody(photoKeys = listOf(tmpPhotoKey(authorId))))
        val text = createdPublicId(authorId, postBody(caption = "텍스트 글"))
        val mine = createdPublicId(userId)
        save(createdPublicId(authorId), onboardedUser()).andExpect(status().isCreated)
        // 게시 순서와 다르게 저장한다
        listOf(text, mine, photo).forEach { save(it, userId).andExpect(status().isCreated) }

        val page = savesPage(userId)

        assertEquals(listOf(photo, mine, text), page.cellIds(), "내 저장만 최근 저장순이어야 한다")
        assertEquals(listOf("PHOTO", "TEXT", "TEXT"), page.feedItems().map { it["mediaType"] }, "칸은 게시물 기준이어야 한다")
        assertEquals(false, page["hasNext"], "마지막 페이지여야 한다")
    }

    @Test
    fun `작성자와 어느 쪽으로든 차단 관계가 되면 숨고 차단을 풀면 다시 나온다`() {
        val userId = onboardedUser()
        val blockedByMe = onboardedUser()
        val blockingMe = onboardedUser()
        val visible = createdPublicId(onboardedUser())
        val blockedPost = createdPublicId(blockedByMe)
        val blockingPost = createdPublicId(blockingMe)
        listOf(visible, blockedPost, blockingPost).forEach { save(it, userId).andExpect(status().isCreated) }

        relate(userId, blockedByMe, "block")
        relate(blockingMe, userId, "block")
        assertEquals(listOf(visible), savesPage(userId).cellIds(), "차단 관계인 작성자의 글은 빠져야 한다")

        unrelate(userId, blockedByMe, "block")
        unrelate(blockingMe, userId, "block")
        assertEquals(listOf(blockingPost, blockedPost, visible), savesPage(userId).cellIds(), "차단을 풀면 저장한 글이 다시 나와야 한다")
    }

    @Test
    fun `작성자를 뮤트하면 숨고 뮤트를 풀면 다시 나온다`() {
        val userId = onboardedUser()
        val mutedAuthor = onboardedUser()
        val visible = createdPublicId(onboardedUser())
        val mutedPost = createdPublicId(mutedAuthor)
        listOf(visible, mutedPost).forEach { save(it, userId).andExpect(status().isCreated) }

        relate(userId, mutedAuthor, "mute")
        assertEquals(listOf(visible), savesPage(userId).cellIds(), "뮤트한 작성자의 글은 빠져야 한다")

        unrelate(userId, mutedAuthor, "mute")
        assertEquals(listOf(mutedPost, visible), savesPage(userId).cellIds(), "뮤트를 풀면 저장한 글이 다시 나와야 한다")
    }

    @Test
    fun `지운 글, 운영자가 지운 글, 탈퇴한 작성자의 글은 나오지 않는다`() {
        val userId = onboardedUser()
        val authorId = onboardedUser()
        val withdrawn = onboardedUser()
        val deleted = createdPublicId(authorId)
        val operatorDeleted = createdPublicId(authorId)
        val withdrawnPost = createdPublicId(withdrawn)
        val visible = createdPublicId(authorId)
        listOf(deleted, operatorDeleted, withdrawnPost, visible).forEach { save(it, userId).andExpect(status().isCreated) }
        deletePost(deleted, authorId).andExpect(status().isNoContent)
        jdbcTemplate.update("update sns_posts set status = 'DELETED', deletion_reason = 'OPERATOR' where public_id = ?", operatorDeleted)
        tx.execute { em.find(User::class.java, withdrawn).withdraw() }

        assertEquals(listOf(visible), savesPage(userId).cellIds(), "볼 수 없는 글은 빠져야 한다")
    }

    @Test
    fun `첫 페이지 뒤에 본 글의 저장을 끄고 다른 글을 저장해도 다음 페이지에 중복과 누락이 없다`() {
        val userId = onboardedUser()
        val authorId = onboardedUser()
        val posts = List(4) { createdPublicId(authorId) }
        posts.forEach { save(it, userId).andExpect(status().isCreated) }
        val first = savesPage(userId, size = 2)
        assertEquals(listOf(posts[3], posts[2]), first.cellIds(), "첫 페이지는 최근 저장 둘이어야 한다")

        // 이미 본 마지막 칸의 저장을 끄고 새 글을 저장한다
        unsave(posts[2], userId).andExpect(status().isNoContent)
        val newlySaved = createdPublicId(authorId)
        save(newlySaved, userId).andExpect(status().isCreated)
        val second = savesPage(userId, cursor = first["nextCursor"] as String, size = 2)

        assertEquals(listOf(posts[1], posts[0]), second.cellIds(), "남은 저장이 빠짐없이 이어지고 새 저장은 들어오지 않아야 한다")
        assertEquals(false, second["hasNext"], "마지막 페이지여야 한다")
        assertEquals(newlySaved, savesPage(userId).cellIds().first(), "처음부터 다시 부르면 새로 저장한 글이 맨 위에 나와야 한다")
    }

    @Test
    fun `저장한 글이 없으면 빈 목록이다`() {
        val page = savesPage(onboardedUser())

        assertEquals(emptyList<String>(), page.cellIds(), "저장한 글이 없으면 빈 목록이어야 한다")
        assertEquals(false, page["hasNext"], "빈 목록은 다음 페이지가 없어야 한다")
        assertNull(page["nextCursor"], "빈 목록은 다음 커서가 없어야 한다")
    }

    @Test
    fun `게시물이 없는 사용자도 저장한 글을 본다`() {
        val userId = onboardedUser()
        val publicId = createdPublicId(onboardedUser())
        save(publicId, userId).andExpect(status().isCreated)

        assertEquals(0, postCountOf(userId), "저장한 사람은 게시물이 없어야 한다")
        assertEquals(listOf(publicId), savesPage(userId).cellIds(), "게시물이 없어도 저장한 글이 나와야 한다")
    }

    @Test
    fun `다른 사용자의 저장됨 커서는 90002다`() {
        val userId = onboardedUser()
        val otherId = onboardedUser()
        val authorId = onboardedUser()
        repeat(2) { save(createdPublicId(authorId), userId).andExpect(status().isCreated) }
        save(createdPublicId(authorId), otherId).andExpect(status().isCreated)
        val cursor = savesPage(userId, size = 1)["nextCursor"] as String

        getSaves(otherId, cursor = cursor)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90002))
    }

    @Test
    fun `토큰 없이 부르면 401이다`() {
        getSaves(null)
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value(20004))
    }

    /** 저장됨 탭을 부른다. userId가 null이면 토큰 없이 부른다. */
    private fun getSaves(
        userId: Long?,
        cursor: String? = null,
        size: Int? = null,
    ): ResultActions {
        val request = get("/api/v1/sns/me/saves")
        cursor?.let { request.param("cursor", it) }
        size?.let { request.param("size", it.toString()) }
        if (userId != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(userId)}")
        return mockMvc.perform(request)
    }

    /** 저장됨 탭 한 페이지를 200으로 받고 data 객체를 돌려준다. */
    private fun savesPage(
        userId: Long,
        cursor: String? = null,
        size: Int? = null,
    ): Map<String, Any?> {
        val json =
            getSaves(userId, cursor, size)
                .andExpect(status().isOk)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)
        return JsonPath.read(json, "$.data")
    }

    /** 탭 페이지 칸의 게시물 publicId를 순서대로 돌려준다. */
    private fun Map<String, Any?>.cellIds(): List<String> = feedItems().map { it["publicId"] as String }
}
