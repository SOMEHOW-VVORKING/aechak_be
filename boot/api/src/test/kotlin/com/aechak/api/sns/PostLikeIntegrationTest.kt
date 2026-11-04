package com.aechak.api.sns

import com.aechak.application.sns.usecase.SnsLikeUseCase
import com.aechak.application.sns.usecase.command.LikePostCommand
import com.aechak.domain.user.user.User
import com.aechak.domain.user.user.enums.UserStatus
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

/**
 * 통합: 게시물 좋아요 켜기와 끄기가 실 보안 필터체인과 MySQL을 지나 계약대로 멱등하게 동작하고 지금의 좋아요 수를 돌려주며 피드 카드의 수와 liked에 보이는지.
 * 깨지면 반복 요청이나 동시 요청이 좋아요 행을 겹치거나 교착으로 실패하거나, 볼 수 없는 게시물의 좋아요가 바뀌거나 그 수가 응답에 실리거나,
 * 카드가 다른 게시물의 수를 싣거나, 오류 코드가 계약과 달라진다.
 */
class PostLikeIntegrationTest : SnsPostIntegrationTestBase() {
    @Autowired
    private lateinit var snsLikeUseCase: SnsLikeUseCase

    @Test
    fun `좋아요는 201이고 응답에 지금의 좋아요 수만 싣는다`() {
        val publicId = createdPublicId(onboardedUser())
        val userId = onboardedUser()

        val data = like(publicId, userId).andExpect(status().isCreated).data()

        assertEquals(mapOf("likeCount" to 1), data, "응답 data에는 likeCount만 있어야 한다")
        assertEquals(1, likeRowCount(userId, publicId), "좋아요 행이 생겨야 한다")
    }

    @Test
    fun `같은 좋아요를 반복해도 201이고 행 1개와 같은 수를 돌려준다`() {
        val publicId = createdPublicId(onboardedUser())
        val userId = onboardedUser()
        like(publicId, userId).andExpect(status().isCreated)

        like(publicId, userId)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.likeCount").value(1))
        assertEquals(1, likeRowCount(userId, publicId), "반복 좋아요가 행을 하나만 남겨야 한다")
    }

    @Test
    fun `끄기는 200이고 좋아요 수 0을 돌려주며 끈 뒤 다시 켜면 1이다`() {
        val publicId = createdPublicId(onboardedUser())
        val userId = onboardedUser()
        like(publicId, userId).andExpect(status().isCreated)

        assertEquals(mapOf("likeCount" to 0), unlike(publicId, userId).andExpect(status().isOk).data())
        assertEquals(0, likeRowCount(userId, publicId), "끄면 좋아요 행이 지워져야 한다")

        like(publicId, userId)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.likeCount").value(1))
    }

    @Test
    fun `좋아요하지 않은 게시물의 끄기는 200이고 다른 사람의 좋아요는 그대로다`() {
        val publicId = createdPublicId(onboardedUser())
        val likerId = onboardedUser()
        like(publicId, likerId).andExpect(status().isCreated)

        unlike(publicId, onboardedUser())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.likeCount").value(1))
        assertEquals(1, likeRowCount(likerId, publicId), "하지 않은 좋아요 끄기가 다른 사람의 좋아요를 지우면 안 된다")
    }

    @Test
    fun `내 게시물에도 좋아요할 수 있다`() {
        val authorId = onboardedUser()
        val publicId = createdPublicId(authorId)

        like(publicId, authorId)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.likeCount").value(1))
    }

    @Test
    fun `없는 게시물, 지운 게시물, 탈퇴한 작성자의 게시물은 켜기와 끄기 모두 404 130400이고 남은 좋아요를 지우지 않는다`() {
        val authorId = onboardedUser()
        val userId = onboardedUser()
        val deleted = createdPublicId(authorId)
        like(deleted, userId).andExpect(status().isCreated)
        deletePost(deleted, authorId).andExpect(status().isNoContent)
        val withdrawn = onboardedUser()
        val withdrawnPost = createdPublicId(withdrawn)
        tx.execute { em.find(User::class.java, withdrawn).withdraw() }

        listOf(MISSING_PUBLIC_ID, deleted, withdrawnPost).forEach {
            expectUnavailable(like(it, userId))
            expectUnavailable(unlike(it, userId))
        }
        assertEquals(1, likeRowCount(userId, deleted), "지운 게시물의 끄기는 막혀 좋아요가 남아야 한다")
        assertEquals(0, likeRowCount(userId, withdrawnPost), "볼 수 없는 게시물에 좋아요 행이 생기면 안 된다")
    }

    @Test
    fun `나를 차단한 작성자의 게시물은 켜기와 끄기 모두 404 130400이고 내가 차단한 작성자의 게시물은 둘 다 403 130001이다`() {
        val authorId = onboardedUser()
        val publicId = createdPublicId(authorId)
        val blockedByAuthor = onboardedUser()
        val blocker = onboardedUser()
        like(publicId, blockedByAuthor).andExpect(status().isCreated)
        like(publicId, blocker).andExpect(status().isCreated)
        relate(authorId, blockedByAuthor, "block")
        relate(blocker, authorId, "block")

        expectUnavailable(like(publicId, blockedByAuthor))
        expectUnavailable(unlike(publicId, blockedByAuthor))
        listOf(like(publicId, blocker), unlike(publicId, blocker)).forEach {
            it
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.errorCode").value(130001))
        }
        assertEquals(1, likeRowCount(blockedByAuthor, publicId), "차단 관계가 된 뒤의 끄기는 막혀 좋아요가 남아야 한다")
        assertEquals(1, likeRowCount(blocker, publicId), "차단 관계가 된 뒤의 끄기는 막혀 좋아요가 남아야 한다")
    }

    @Test
    fun `차단 전에 남긴 좋아요는 차단 뒤에도 행과 수에 남는다`() {
        val authorId = onboardedUser()
        val publicId = createdPublicId(authorId)
        val likerId = onboardedUser()
        like(publicId, likerId).andExpect(status().isCreated)
        relate(authorId, likerId, "block")

        assertEquals(1, likeRowCount(likerId, publicId), "차단이 좋아요 행을 지우면 안 된다")
        like(publicId, onboardedUser())
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.likeCount").value(2))
    }

    @Test
    fun `작성자가 뮤트한 사용자의 좋아요도 등록되고 수에 들어간다`() {
        val authorId = onboardedUser()
        val publicId = createdPublicId(authorId)
        val mutedId = onboardedUser()
        relate(authorId, mutedId, "mute")

        like(publicId, mutedId)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.likeCount").value(1))
    }

    @Test
    fun `이용 제한 작성자와 만료 전 임시 정지 작성자의 게시물에도 좋아요할 수 있다`() {
        val restricted = onboardedUser()
        val restrictedPost = createdPublicId(restricted)
        val suspended = onboardedUser()
        val suspendedPost = createdPublicId(suspended)
        sanction(restricted, UserStatus.RESTRICTED, null)
        sanction(suspended, UserStatus.SUSPENDED, LocalDateTime.now().plusDays(7))

        listOf(restrictedPost, suspendedPost).forEach {
            like(it, onboardedUser())
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.data.likeCount").value(1))
        }
    }

    @Test
    fun `탐색 피드 카드의 좋아요 수와 liked는 게시물마다 채우고 비로그인은 liked가 false다`() {
        val viewerId = onboardedUser()
        val authorId = onboardedUser()
        val likedByMe = createdPublicId(authorId)
        val likedByOthers = createdPublicId(authorId)
        like(likedByMe, viewerId).andExpect(status().isCreated)
        repeat(2) { like(likedByOthers, onboardedUser()).andExpect(status().isCreated) }

        assertEquals(
            mapOf(likedByMe to (1 to true), likedByOthers to (2 to false)),
            likesOfCards(feedPage("explore", viewerId)),
            "카드마다 자기 게시물의 좋아요 수와 내 좋아요 여부가 실려야 한다",
        )
        assertEquals(
            mapOf(likedByMe to (1 to false), likedByOthers to (2 to false)),
            likesOfCards(feedPage("explore")),
            "비로그인 카드는 수는 같고 liked가 false여야 한다",
        )
    }

    @Nested
    inner class Concurrency {
        @Test
        fun `같은 사용자의 동시 좋아요는 행 1개만 남긴다`() {
            val publicId = createdPublicId(onboardedUser())
            val userId = onboardedUser()

            val errors = runConcurrently(List(6) { { snsLikeUseCase.likePost(LikePostCommand(userId, publicId)) } })

            assertTrue(errors.isEmpty(), "동시 좋아요에서 오류가 나면 안 된다: $errors")
            assertEquals(1, likeRowCount(userId, publicId), "동시 좋아요가 행을 하나만 남겨야 한다")
        }

        @Test
        fun `여러 사용자가 동시에 좋아요하면 좋아요 수가 사용자 수와 같다`() {
            val publicId = createdPublicId(onboardedUser())
            val userIds = List(8) { onboardedUser() }

            val errors = runConcurrently(userIds.map { { snsLikeUseCase.likePost(LikePostCommand(it, publicId)) } })

            assertTrue(errors.isEmpty(), "여러 사용자의 동시 좋아요에서 오류가 나면 안 된다: $errors")
            unlike(publicId, onboardedUser())
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.likeCount").value(userIds.size))
        }

        @Test
        fun `좋아요한 상태에서 끄기 하나와 켜기 둘을 동시에 보내도 교착 없이 모두 성공하고 행은 많아야 1개다`() {
            repeat(10) {
                val publicId = createdPublicId(onboardedUser())
                val userId = onboardedUser()
                like(publicId, userId).andExpect(status().isCreated)
                val command = LikePostCommand(userId, publicId)

                // 끄기가 지운 행을 두 켜기가 함께 기다리다 끄기가 커밋되면 둘이 같은 키에 삽입하려고 다툰다
                val errors =
                    runConcurrently(
                        listOf(
                            { snsLikeUseCase.unlikePost(command) },
                            { snsLikeUseCase.likePost(command) },
                            { snsLikeUseCase.likePost(command) },
                        ),
                    )

                assertTrue(errors.isEmpty(), "끄기와 켜기의 동시 요청에서 교착이나 오류가 나면 안 된다: $errors")
                assertTrue(likeRowCount(userId, publicId) <= 1, "좋아요 행은 많아야 1개여야 한다")
            }
        }
    }

    /** user가 이 게시물에 누른 좋아요 행 수. */
    private fun likeRowCount(
        userId: Long,
        publicId: String,
    ): Int =
        jdbcTemplate.queryForObject(
            "select count(*) from sns_post_likes l join sns_posts p on p.id = l.post_id where l.user_id = ? and p.public_id = ?",
            Int::class.java,
            userId,
            publicId,
        )!!

    private fun sanction(
        userId: Long,
        status: UserStatus,
        until: LocalDateTime?,
    ) {
        tx.execute {
            em
                .createQuery("update User u set u.status = :st, u.sanctionUntil = :until where u.id = :id")
                .setParameter("st", status)
                .setParameter("until", until)
                .setParameter("id", userId)
                .executeUpdate()
        }
    }

    /** 피드 페이지 카드마다 게시물 publicId와 좋아요 수, liked. */
    private fun likesOfCards(page: Map<String, Any?>): Map<Any?, Pair<Any?, Any?>> =
        page.feedItems().map { it["post"] as Map<*, *> }.associate { it["publicId"] to (it["likeCount"] to it["liked"]) }

    private fun ResultActions.data(): Map<String, Any?> = JsonPath.read(andReturn().response.getContentAsString(Charsets.UTF_8), "$.data")

    private fun expectUnavailable(result: ResultActions) {
        result
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(130400))
    }

    companion object {
        private const val MISSING_PUBLIC_ID = "01JF9W3Q6R8T2V4X7Z9B1C3D5J"
    }
}
