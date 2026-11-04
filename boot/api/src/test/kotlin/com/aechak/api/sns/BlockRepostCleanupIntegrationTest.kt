package com.aechak.api.sns

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 통합: 차단하면 그 요청 안에서 두 사람이 서로의 글에 한 리포스트를 지우고 원글 수를 지운 만큼만 내리는지.
 * 깨지면 차단한 상대의 글이 내 리포스트로 내 팔로워에게 계속 보이거나 원글의 리포스트 수가 리포스트 행 수와 어긋난다.
 */
class BlockRepostCleanupIntegrationTest : SnsPostIntegrationTestBase() {
    @Test
    fun `내가 상대를 차단하면 두 사람이 서로의 글에 한 리포스트가 지워지고 두 원글의 수가 1씩 준다`() {
        val s = repostedEachOther()

        relate(s.me, s.other, "block")

        expectCleanedUp(s)
    }

    @Test
    fun `상대가 나를 차단해도 두 사람이 서로의 글에 한 리포스트가 지워지고 두 원글의 수가 1씩 준다`() {
        val s = repostedEachOther()

        relate(s.other, s.me, "block")

        expectCleanedUp(s)
    }

    @Test
    fun `차단해도 제3자의 리포스트는 남는다`() {
        val s = repostedEachOther()

        relate(s.me, s.other, "block")

        assertEquals(1, repostRowCount(s.third, s.myPost), "제3자가 내 글에 한 리포스트는 남아야 한다")
        assertEquals(1, repostRowCount(s.third, s.otherPost), "제3자가 상대 글에 한 리포스트는 남아야 한다")
    }

    @Test
    fun `차단을 풀어도 지운 리포스트와 수는 돌아오지 않는다`() {
        val s = repostedEachOther()
        relate(s.me, s.other, "block")

        mockMvc
            .perform(
                delete("/api/v1/sns/users/${publicIdOf(s.other)}/block")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(s.me)}"),
            ).andExpect(status().isNoContent)

        expectCleanedUp(s)
    }

    @Test
    fun `이미 차단 중인 상대를 다시 차단해도 수는 그대로다`() {
        val s = repostedEachOther()
        relate(s.me, s.other, "block")

        relate(s.me, s.other, "block")

        expectCleanedUp(s)
    }

    /** 두 사람이 서로의 글을 리포스트하고 제3자도 두 글을 리포스트해 두 원글의 수가 2인 상태 */
    private fun repostedEachOther(): Scenario {
        val me = onboardedUser()
        val other = onboardedUser()
        val s = Scenario(me, other, onboardedUser(), createdPublicId(me), createdPublicId(other))
        // 수는 0보다 클 때만 내리므로 제3자의 리포스트 없이 1에서 시작하면 잘못 내린 것이 드러나지 않는다
        listOf(s.third to s.myPost, s.third to s.otherPost, s.me to s.otherPost, s.other to s.myPost).forEach { (userId, publicId) ->
            repost(publicId, userId).andExpect(status().isCreated)
        }
        return s
    }

    private fun expectCleanedUp(s: Scenario) {
        assertEquals(0, repostRowCount(s.me, s.otherPost), "내가 상대 글에 한 리포스트가 지워져야 한다")
        assertEquals(0, repostRowCount(s.other, s.myPost), "상대가 내 글에 한 리포스트가 지워져야 한다")
        assertEquals(1, repostCountOf(s.otherPost), "상대 글의 리포스트 수가 2에서 1이 되어야 한다")
        assertEquals(1, repostCountOf(s.myPost), "내 글의 리포스트 수가 2에서 1이 되어야 한다")
    }

    private data class Scenario(
        val me: Long,
        val other: Long,
        val third: Long,
        val myPost: String,
        val otherPost: String,
    )
}
