package com.aechak.api.sns

import com.aechak.application.sns.usecase.SnsPostUseCase
import com.aechak.application.sns.usecase.command.SavePostCommand
import com.aechak.domain.user.user.User
import com.aechak.domain.user.user.enums.UserStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 통합: 저장 켜기와 끄기가 실 보안 필터체인과 MySQL을 지나 계약대로 멱등하게 동작하고 상세와 피드 카드의 saved에 보이며 어떤 수도 바꾸지 않는지.
 * 깨지면 반복 요청이나 동시 요청이 저장 행을 겹치거나 교착으로 실패하거나 볼 수 없는 게시물이 저장되거나 오류 코드가 계약과 달라진다.
 */
class SaveIntegrationTest : SnsPostIntegrationTestBase() {
    @Autowired
    private lateinit var snsPostUseCase: SnsPostUseCase

    @Test
    fun `저장은 201이고 상세의 saved가 true이며 취소는 204이고 다시 false다`() {
        val publicId = createdPublicId(onboardedUser())
        val userId = onboardedUser()

        save(publicId, userId).andExpect(status().isCreated)
        assertEquals(1, saveRowCount(userId, publicId), "저장 행이 생겨야 한다")
        assertEquals(true, postDetail(publicId, userId)["saved"], "저장하면 상세의 saved가 true여야 한다")

        unsave(publicId, userId).andExpect(status().isNoContent)
        assertEquals(0, saveRowCount(userId, publicId), "취소하면 저장 행이 지워져야 한다")
        assertEquals(false, postDetail(publicId, userId)["saved"], "취소하면 상세의 saved가 false여야 한다")
    }

    @Test
    fun `반복 저장과 하지 않은 저장 취소는 성공 코드이고 저장을 하나로 유지한다`() {
        val publicId = createdPublicId(onboardedUser())
        val userId = onboardedUser()

        save(publicId, userId).andExpect(status().isCreated)
        save(publicId, userId).andExpect(status().isCreated)
        assertEquals(1, saveRowCount(userId, publicId), "반복 저장이 행을 하나만 남겨야 한다")

        unsave(publicId, onboardedUser()).andExpect(status().isNoContent)
        assertEquals(1, saveRowCount(userId, publicId), "하지 않은 저장 취소가 다른 사람의 저장을 지우면 안 된다")

        unsave(publicId, userId).andExpect(status().isNoContent)
        unsave(publicId, userId).andExpect(status().isNoContent)
        assertEquals(0, saveRowCount(userId, publicId), "반복 취소 뒤에는 저장 행이 없어야 한다")
    }

    @Test
    fun `내 글도 저장할 수 있다`() {
        val authorId = onboardedUser()
        val publicId = createdPublicId(authorId)

        save(publicId, authorId).andExpect(status().isCreated)
        assertEquals(true, postDetail(publicId, authorId)["saved"], "내 글을 저장하면 saved가 true여야 한다")
    }

    @Test
    fun `저장해도 작성자가 보는 상세는 그대로이고 저장 수 필드가 없다`() {
        val authorId = onboardedUser()
        val publicId = createdPublicId(authorId)
        repost(publicId, onboardedUser()).andExpect(status().isCreated)
        val before = postDetail(publicId, authorId)

        save(publicId, onboardedUser()).andExpect(status().isCreated)
        save(publicId, onboardedUser()).andExpect(status().isCreated)

        val after = postDetail(publicId, authorId)
        assertEquals(before, after, "저장이 작성자가 보는 상세의 수나 상태를 바꾸면 안 된다")
        assertTrue("saveCount" !in after, "저장 수 필드가 없어야 한다")
    }

    @Test
    fun `없는 글, 지운 글, 운영자가 지운 글, 탈퇴한 작성자의 글 저장은 404 130400이다`() {
        val authorId = onboardedUser()
        val userId = onboardedUser()
        val deletedByAuthor = createdPublicId(authorId)
        deletePost(deletedByAuthor, authorId).andExpect(status().isNoContent)
        val deletedByOperator = createdPublicId(authorId)
        jdbcTemplate.update(
            "update sns_posts set status = 'DELETED', deletion_reason = 'OPERATOR' where public_id = ?",
            deletedByOperator,
        )
        val withdrawn = onboardedUser()
        val withdrawnPost = createdPublicId(withdrawn)
        tx.execute { em.find(User::class.java, withdrawn).withdraw() }

        expectUnavailable(save(MISSING_PUBLIC_ID, userId))
        listOf(deletedByAuthor, deletedByOperator, withdrawnPost).forEach {
            expectUnavailable(save(it, userId))
            assertEquals(0, saveRowCount(userId, it), "볼 수 없는 글에 저장 행이 생기면 안 된다: $it")
        }
    }

    @Test
    fun `나를 차단한 작성자와 서로 차단한 작성자의 글은 404 130400이고 내가 차단한 작성자의 글은 403 130001이다`() {
        val authorId = onboardedUser()
        val publicId = createdPublicId(authorId)
        val blockedByAuthor = onboardedUser()
        relate(authorId, blockedByAuthor, "block")
        val mutual = onboardedUser()
        relate(authorId, mutual, "block")
        relate(mutual, authorId, "block")
        val blocker = onboardedUser()
        relate(blocker, authorId, "block")

        expectUnavailable(save(publicId, blockedByAuthor))
        expectUnavailable(save(publicId, mutual))
        save(publicId, blocker)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(130001))
        listOf(blockedByAuthor, mutual, blocker).forEach {
            assertEquals(0, saveRowCount(it, publicId), "차단 관계의 글에 저장 행이 생기면 안 된다")
        }
    }

    @Test
    fun `뮤트한 작성자의 글도 저장할 수 있다`() {
        val authorId = onboardedUser()
        val userId = onboardedUser()
        val publicId = createdPublicId(authorId)
        relate(userId, authorId, "mute")

        save(publicId, userId).andExpect(status().isCreated)
        assertEquals(1, saveRowCount(userId, publicId), "뮤트한 작성자의 글도 저장 행이 생겨야 한다")
    }

    @Test
    fun `탐색 피드 카드의 saved는 내가 저장한 글만 true이고 비로그인은 false다`() {
        val viewerId = onboardedUser()
        val authorId = onboardedUser()
        val savedByMe = createdPublicId(authorId)
        val savedByOther = createdPublicId(authorId)
        save(savedByMe, viewerId).andExpect(status().isCreated)
        save(savedByOther, onboardedUser()).andExpect(status().isCreated)

        assertEquals(
            mapOf(savedByMe to true, savedByOther to false),
            savedOfCards(feedPage("explore", viewerId)),
            "내가 저장한 글의 카드만 saved가 true여야 한다",
        )
        assertEquals(
            mapOf(savedByMe to false, savedByOther to false),
            savedOfCards(feedPage("explore")),
            "비로그인 카드는 saved가 false여야 한다",
        )
    }

    @Test
    fun `없는 publicId 취소는 404 130400이고 지운 글의 저장 취소는 204이며 저장을 지운다`() {
        val authorId = onboardedUser()
        val userId = onboardedUser()
        val publicId = createdPublicId(authorId)
        save(publicId, userId).andExpect(status().isCreated)
        deletePost(publicId, authorId).andExpect(status().isNoContent)

        expectUnavailable(unsave(MISSING_PUBLIC_ID, userId))
        unsave(publicId, userId).andExpect(status().isNoContent)
        assertEquals(0, saveRowCount(userId, publicId), "지운 글의 저장도 취소로 지워져야 한다")
    }

    @Test
    fun `이용 제한 계정의 저장과 취소는 403 20013이다`() {
        val publicId = createdPublicId(onboardedUser())
        val userId = onboardedUser()
        tx.execute {
            em
                .createQuery("update User u set u.status = :st where u.id = :id")
                .setParameter("st", UserStatus.RESTRICTED)
                .setParameter("id", userId)
                .executeUpdate()
        }

        save(publicId, userId)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(20013))
        unsave(publicId, userId)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(20013))
    }

    @Nested
    inner class Concurrency {
        @Test
        fun `같은 저장을 동시에 보내도 행 1개만 남는다`() {
            val publicId = createdPublicId(onboardedUser())
            val userId = onboardedUser()

            val errors = runConcurrently(List(6) { { snsPostUseCase.save(SavePostCommand(userId, publicId)) } })

            assertTrue(errors.isEmpty(), "동시 저장에서 오류가 나면 안 된다: $errors")
            assertEquals(1, saveRowCount(userId, publicId), "동시 저장이 행을 하나만 남겨야 한다")
        }

        @Test
        fun `저장된 상태에서 취소 하나와 저장 둘을 동시에 보내도 교착 없이 모두 성공하고 행은 많아야 1개다`() {
            repeat(10) {
                val publicId = createdPublicId(onboardedUser())
                val userId = onboardedUser()
                save(publicId, userId).andExpect(status().isCreated)
                val command = SavePostCommand(userId, publicId)

                // 취소가 지운 행을 두 저장이 함께 기다리다 취소가 커밋되면 둘이 같은 키에 삽입하려고 다툰다
                val errors =
                    runConcurrently(
                        listOf(
                            { snsPostUseCase.unsave(command) },
                            { snsPostUseCase.save(command) },
                            { snsPostUseCase.save(command) },
                        ),
                    )

                assertTrue(errors.isEmpty(), "취소와 저장의 동시 요청에서 교착이나 오류가 나면 안 된다: $errors")
                assertTrue(saveRowCount(userId, publicId) <= 1, "저장 행은 많아야 1개여야 한다")
            }
        }
    }

    /** user가 이 게시물에 한 저장 행 수. */
    private fun saveRowCount(
        userId: Long,
        publicId: String,
    ): Int =
        jdbcTemplate.queryForObject(
            "select count(*) from sns_saves s join sns_posts p on p.id = s.post_id where s.user_id = ? and p.public_id = ?",
            Int::class.java,
            userId,
            publicId,
        )!!

    /** 피드 페이지 카드마다 게시물 publicId와 saved. */
    private fun savedOfCards(page: Map<String, Any?>): Map<Any?, Any?> =
        page.feedItems().map { it["post"] as Map<*, *> }.associate { it["publicId"] to it["saved"] }

    private fun expectUnavailable(result: ResultActions) {
        result
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(130400))
    }

    companion object {
        private const val MISSING_PUBLIC_ID = "01JF9W3Q6R8T2V4X7Z9B1C3D5J"
    }
}
