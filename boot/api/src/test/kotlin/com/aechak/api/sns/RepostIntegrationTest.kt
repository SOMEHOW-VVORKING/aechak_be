package com.aechak.api.sns

import com.aechak.application.sns.usecase.SnsPostUseCase
import com.aechak.application.sns.usecase.command.RepostPostCommand
import com.aechak.domain.user.user.User
import com.aechak.domain.user.user.enums.UserStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 통합: 리포스트 켜기와 끄기가 실 보안 필터체인과 MySQL을 지나 계약대로 멱등하게 동작하고 원글의 리포스트 수가 리포스트 행 수와 같게 움직이는지.
 * 깨지면 반복 요청이나 동시 요청이 수를 어긋나게 하거나 볼 수 없는 원글에 리포스트가 생기거나 판정 순서와 오류 코드가 계약과 달라진다.
 */
class RepostIntegrationTest : SnsPostIntegrationTestBase() {
    @Autowired
    private lateinit var snsPostUseCase: SnsPostUseCase

    @Test
    fun `리포스트는 201이고 원글의 리포스트 수를 1 올리며 취소는 204이고 다시 내린다`() {
        val authorId = onboardedUser()
        val userId = onboardedUser()
        val publicId = createdPublicId(authorId)

        repost(publicId, userId).andExpect(status().isCreated)
        assertEquals(1, repostRowCount(userId, publicId), "리포스트 행이 생겨야 한다")
        assertEquals(1, repostCountOf(publicId), "원글의 리포스트 수가 1이어야 한다")

        unrepost(publicId, userId).andExpect(status().isNoContent)
        assertEquals(0, repostRowCount(userId, publicId), "취소하면 리포스트 행이 지워져야 한다")
        assertEquals(0, repostCountOf(publicId), "취소하면 리포스트 수가 0이어야 한다")
    }

    @Test
    fun `반복 리포스트와 하지 않은 리포스트 취소는 성공 코드이고 수를 바꾸지 않는다`() {
        val publicId = createdPublicId(onboardedUser())
        // 수는 0보다 클 때만 내리므로 다른 사람의 리포스트로 1에서 시작해야 잘못 내린 것이 드러난다
        repost(publicId, onboardedUser()).andExpect(status().isCreated)
        val userId = onboardedUser()

        repost(publicId, userId).andExpect(status().isCreated)
        repost(publicId, userId).andExpect(status().isCreated)
        assertEquals(1, repostRowCount(userId, publicId), "반복 리포스트가 행을 하나만 남겨야 한다")
        assertEquals(2, repostCountOf(publicId), "반복 리포스트가 수를 다시 올리면 안 된다")

        unrepost(publicId, onboardedUser()).andExpect(status().isNoContent)
        assertEquals(2, repostCountOf(publicId), "하지 않은 리포스트 취소가 수를 내리면 안 된다")
    }

    @Test
    fun `본인 원글 리포스트는 400 130600이고 행과 수를 만들지 않는다`() {
        val authorId = onboardedUser()
        val publicId = createdPublicId(authorId)

        repost(publicId, authorId)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(130600))
        assertEquals(0, repostRowCount(authorId, publicId), "본인 리포스트 행이 생기면 안 된다")
        assertNull(repostCountOf(publicId), "본인 리포스트가 수를 만들면 안 된다")
    }

    @Test
    fun `작성자가 지운 원글과 운영자가 지운 원글의 리포스트는 404 130400이다`() {
        val authorId = onboardedUser()
        val userId = onboardedUser()
        val deletedByAuthor = createdPublicId(authorId)
        deletePost(deletedByAuthor, authorId).andExpect(status().isNoContent)
        val deletedByOperator = createdPublicId(authorId)
        jdbcTemplate.update(
            "update sns_posts set status = 'DELETED', deletion_reason = 'OPERATOR' where public_id = ?",
            deletedByOperator,
        )

        expectUnavailable(repost(deletedByAuthor, userId))
        expectUnavailable(repost(deletedByOperator, userId))
        assertEquals(0, repostRowCount(userId, deletedByOperator), "지운 원글에 리포스트 행이 생기면 안 된다")
    }

    @Test
    fun `탈퇴한 작성자의 원글 리포스트는 404 130400이다`() {
        val authorId = onboardedUser()
        val publicId = createdPublicId(authorId)
        tx.execute { em.find(User::class.java, authorId).withdraw() }

        expectUnavailable(repost(publicId, onboardedUser()))
    }

    @Test
    fun `나를 차단한 작성자와 서로 차단한 작성자의 원글 리포스트는 404 130400이다`() {
        val authorId = onboardedUser()
        val publicId = createdPublicId(authorId)
        val blockedByAuthor = onboardedUser()
        relate(authorId, blockedByAuthor, "block")
        val mutual = onboardedUser()
        relate(authorId, mutual, "block")
        relate(mutual, authorId, "block")

        expectUnavailable(repost(publicId, blockedByAuthor))
        expectUnavailable(repost(publicId, mutual))
        assertNull(repostCountOf(publicId), "차단 관계의 리포스트가 수를 만들면 안 된다")
    }

    @Test
    fun `내가 차단한 작성자의 원글 리포스트는 403 130001이다`() {
        val authorId = onboardedUser()
        val userId = onboardedUser()
        val publicId = createdPublicId(authorId)
        relate(userId, authorId, "block")

        repost(publicId, userId)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(130001))
        assertEquals(0, repostRowCount(userId, publicId), "내가 차단한 작성자의 원글에 리포스트 행이 생기면 안 된다")
    }

    @Test
    fun `뮤트한 작성자의 원글도 리포스트할 수 있다`() {
        val authorId = onboardedUser()
        val userId = onboardedUser()
        val publicId = createdPublicId(authorId)
        relate(userId, authorId, "mute")

        repost(publicId, userId).andExpect(status().isCreated)
        assertEquals(1, repostCountOf(publicId), "뮤트한 작성자의 원글도 리포스트 수가 올라야 한다")
    }

    @Test
    fun `없는 publicId 취소는 404 130400이고 지운 원글의 리포스트 취소는 204이며 수를 내린다`() {
        val authorId = onboardedUser()
        val userId = onboardedUser()
        val publicId = createdPublicId(authorId)
        repost(publicId, userId).andExpect(status().isCreated)
        deletePost(publicId, authorId).andExpect(status().isNoContent)

        expectUnavailable(unrepost("01JF9W3Q6R8T2V4X7Z9B1C3D5J", userId))
        unrepost(publicId, userId).andExpect(status().isNoContent)
        assertEquals(0, repostRowCount(userId, publicId), "지운 원글의 리포스트도 취소로 지워져야 한다")
        assertEquals(0, repostCountOf(publicId), "지운 원글의 리포스트 취소도 수를 내려야 한다")
    }

    @Test
    fun `이용 제한 계정의 리포스트와 취소는 403 20013이다`() {
        val publicId = createdPublicId(onboardedUser())
        val userId = onboardedUser()
        tx.execute {
            em
                .createQuery("update User u set u.status = :st where u.id = :id")
                .setParameter("st", UserStatus.RESTRICTED)
                .setParameter("id", userId)
                .executeUpdate()
        }

        repost(publicId, userId)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(20013))
        unrepost(publicId, userId)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(20013))
    }

    @Nested
    inner class Concurrency {
        @Test
        fun `같은 리포스트를 동시에 보내도 행 1개만 남고 수는 한 번만 오른다`() {
            val publicId = createdPublicId(onboardedUser())
            repost(publicId, onboardedUser()).andExpect(status().isCreated)
            val userId = onboardedUser()

            val errors = runConcurrently(List(6) { { snsPostUseCase.repost(RepostPostCommand(userId, publicId)) } })

            assertTrue(errors.isEmpty(), "동시 리포스트에서 오류가 나면 안 된다: $errors")
            assertEquals(1, repostRowCount(userId, publicId), "동시 리포스트가 행을 하나만 남겨야 한다")
            assertEquals(2, repostCountOf(publicId), "다른 사람의 리포스트 1에서 한 번만 올라야 한다")
        }
    }

    private fun expectUnavailable(result: ResultActions) {
        result
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(130400))
    }
}
