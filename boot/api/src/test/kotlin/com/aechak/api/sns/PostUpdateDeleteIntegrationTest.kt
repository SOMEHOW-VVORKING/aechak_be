package com.aechak.api.sns

import com.aechak.application.sns.postdeletion.usecase.SnsPostDeletionUseCase
import com.aechak.application.sns.usecase.SnsPostUseCase
import com.aechak.application.sns.usecase.command.UpdatePostCommand
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 통합 — 작성자의 게시물 삭제와 수정이 소유권, 이미 지운 게시물, 수, 파일 정리를 계약대로 처리하는지.
 */
class PostUpdateDeleteIntegrationTest : SnsPostIntegrationTestBase() {
    @Autowired
    private lateinit var snsPostDeletionUseCase: SnsPostDeletionUseCase

    @Autowired
    private lateinit var snsPostUseCase: SnsPostUseCase

    @Test
    fun `작성자가 지우면 204이고 상태와 사유와 시각을 남기고 캡션을 지운다`() {
        val authorId = onboardedUser()
        val publicId = createdPublicId(authorId)

        deletePost(publicId, authorId).andExpect(status().isNoContent)

        val row = postRow(publicId)
        assertEquals("DELETED", row["status"])
        assertEquals("AUTHOR", row["deletion_reason"])
        assertNotNull(row["deleted_at"])
        assertNull(row["caption"])
    }

    @Test
    fun `지우면 작성자 게시물 수가 한 번 준다`() {
        val authorId = onboardedUser()
        val publicId = createdPublicId(authorId)
        createdPublicId(authorId)

        deletePost(publicId, authorId).andExpect(status().isNoContent)

        assertEquals(1, statsPostCount(authorId))
    }

    @Test
    fun `이미 지운 게시물을 다시 지워도 204이고 수는 그대로다`() {
        val authorId = onboardedUser()
        val publicId = createdPublicId(authorId)
        createdPublicId(authorId)
        deletePost(publicId, authorId).andExpect(status().isNoContent)

        deletePost(publicId, authorId).andExpect(status().isNoContent)

        assertEquals(1, statsPostCount(authorId))
    }

    @Test
    fun `지우면 사진 파일을 모두 지운다`() {
        val authorId = onboardedUser()
        val tmpKeys = List(3) { tmpPhotoKey(authorId) }
        val publicId = createdPublicId(authorId, postBody(photoKeys = tmpKeys))

        deletePost(publicId, authorId).andExpect(status().isNoContent)

        assertTrue(fakeFileStorage.deletedKeys.containsAll(tmpKeys.map(::promotedKeyOf)))
    }

    @Test
    fun `영상 게시물을 지우면 영상과 썸네일 파일을 모두 지운다`() {
        val authorId = onboardedUser()
        val videoKey = tmpVideoKey(authorId)
        val thumbnailKey = tmpPhotoKey(authorId)
        val publicId = createdPublicId(authorId, postBody(caption = null, video = videoJson(videoKey, thumbnailKey)))

        deletePost(publicId, authorId).andExpect(status().isNoContent)

        assertTrue(fakeFileStorage.deletedKeys.containsAll(listOf(promotedKeyOf(videoKey), promotedKeyOf(thumbnailKey))))
    }

    @Test
    fun `같은 tmp 키로 게시한 두 게시물 중 하나를 지워도 다른 쪽 파일은 남고 지운 파일을 다시 만들지 않는다`() {
        val authorId = onboardedUser()
        val tmpKey = tmpPhotoKey(authorId)
        fakeFileStorage.failDeletesOf(setOf(tmpKey))
        val first = createdPublicId(authorId, postBody(photoKeys = listOf(tmpKey)))
        val second = createdPublicId(authorId, postBody(photoKeys = listOf(tmpKey)))

        deletePost(first, authorId).andExpect(status().isNoContent)
        val third = createdPublicId(authorId, postBody(photoKeys = listOf(tmpKey)))

        val firstKey = mediaKeysOf(first).single()
        assertTrue(firstKey in fakeFileStorage.deletedKeys)
        assertTrue((mediaKeysOf(second) + mediaKeysOf(third)).none { it in fakeFileStorage.deletedKeys })
        assertEquals(1, fakeFileStorage.promotedKeys.count { it == firstKey })
    }

    @Test
    fun `다른 사람 게시물을 지우면 403 130401이다`() {
        val publicId = createdPublicId(onboardedUser())

        deletePost(publicId, onboardedUser())
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(130401))
        assertEquals("PUBLISHED", postRow(publicId)["status"])
    }

    @Test
    fun `없는 게시물을 지우면 404 130400이다`() {
        deletePost("01JF9W3Q6R8T2V4X7Z9B1C3D5J", onboardedUser())
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(130400))
    }

    @Test
    fun `파일 삭제가 실패해도 204이고 다시 지우면 그 파일을 지운다`() {
        val authorId = onboardedUser()
        val tmpKey = tmpPhotoKey(authorId)
        val publicId = createdPublicId(authorId, postBody(photoKeys = listOf(tmpKey)))
        val photoKey = promotedKeyOf(tmpKey)
        fakeFileStorage.failDeletesOf(setOf(photoKey))

        deletePost(publicId, authorId).andExpect(status().isNoContent)

        assertEquals("DELETED", postRow(publicId)["status"])
        assertTrue(photoKey !in fakeFileStorage.deletedKeys)

        fakeFileStorage.failDeletesOf(emptySet())
        deletePost(publicId, authorId).andExpect(status().isNoContent)

        assertTrue(photoKey in fakeFileStorage.deletedKeys)
    }

    @Test
    fun `캡션을 고치면 204이고 상세의 edited가 true다`() {
        val authorId = onboardedUser()
        val publicId = createdPublicId(authorId)

        updatePost(publicId, authorId, updateBody(caption = "공원 다녀왔어요")).andExpect(status().isNoContent)

        val detail = postDetail(publicId)
        assertEquals("공원 다녀왔어요", detail["caption"])
        assertEquals(true, detail["edited"])
    }

    @Test
    fun `바뀐 것 없이 저장하면 204이고 edited_at이 그대로다`() {
        val authorId = onboardedUser()
        val publicId = createdPublicId(authorId)
        updatePost(publicId, authorId, updateBody(caption = "공원 다녀왔어요")).andExpect(status().isNoContent)
        val editedAt = assertNotNull(postRow(publicId)["edited_at"])

        updatePost(publicId, authorId, updateBody(caption = " 공원 다녀왔어요 ")).andExpect(status().isNoContent)

        assertEquals(editedAt, postRow(publicId)["edited_at"])
    }

    @Test
    fun `다른 사람 게시물을 고치면 403 130401이고 캡션이 그대로다`() {
        val publicId = createdPublicId(onboardedUser())

        updatePost(publicId, onboardedUser(), updateBody())
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(130401))
        assertEquals("산책 다녀왔어요", postRow(publicId)["caption"])
    }

    @Test
    fun `지운 게시물을 고치면 404 130400이다`() {
        val authorId = onboardedUser()
        val publicId = createdPublicId(authorId)
        deletePost(publicId, authorId).andExpect(status().isNoContent)

        updatePost(publicId, authorId, updateBody())
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(130400))
        assertNull(postRow(publicId)["caption"])
    }

    @Test
    fun `없는 게시물을 고치면 404 130400이지만 규칙을 어긴 수정은 400이 먼저다`() {
        val userId = onboardedUser()

        updatePost("01JF9W3Q6R8T2V4X7Z9B1C3D5J", userId, updateBody())
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(130400))
        updatePost("01JF9W3Q6R8T2V4X7Z9B1C3D5J", userId, updateBody(caption = "가".repeat(2_201)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(130403))
    }

    @Test
    fun `텍스트 게시물의 캡션을 비우면 400 130402이고 캡션이 그대로다`() {
        val authorId = onboardedUser()
        val publicId = createdPublicId(authorId)

        updatePost(publicId, authorId, updateBody(caption = null))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(130402))
        assertEquals("산책 다녀왔어요", postRow(publicId)["caption"])
    }

    @Test
    fun `caption이나 hashtags를 빠뜨리면 90001이고 캡션이 그대로다`() {
        val authorId = onboardedUser()
        val publicId = createdPublicId(authorId, postBody(photoKeys = listOf(tmpPhotoKey(authorId))))

        updatePost(publicId, authorId, """{"hashtags":[]}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
        updatePost(publicId, authorId, """{"caption":"공원 다녀왔어요"}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
        assertEquals("산책 다녀왔어요", postRow(publicId)["caption"])
    }

    @Test
    fun `caption이 null이면 사진 게시물의 캡션을 지운다`() {
        val authorId = onboardedUser()
        val publicId = createdPublicId(authorId, postBody(photoKeys = listOf(tmpPhotoKey(authorId))))

        updatePost(publicId, authorId, updateBody(caption = null)).andExpect(status().isNoContent)

        assertNull(postRow(publicId)["caption"])
        assertEquals(true, postDetail(publicId)["edited"])
    }

    @Test
    fun `사진을 함께 보내도 작성 시각과 미디어는 그대로다`() {
        val authorId = onboardedUser()
        val tmpKeys = List(2) { tmpPhotoKey(authorId) }
        val publicId = createdPublicId(authorId, postBody(photoKeys = tmpKeys))
        val createdAt = postRow(publicId)["created_at"]
        val body = """{"caption":"공원 다녀왔어요","hashtags":[],"photoKeys":["${tmpPhotoKey(authorId)}"]}"""

        updatePost(publicId, authorId, body).andExpect(status().isNoContent)

        assertEquals(createdAt, postRow(publicId)["created_at"])
        assertEquals(tmpKeys.map(::promotedKeyOf), mediaKeysOf(publicId))
    }

    @Nested
    inner class Concurrency {
        @Test
        fun `같은 게시물을 동시에 두 번 지워도 수는 한 번만 준다`() {
            val authorId = onboardedUser()
            val publicId = createdPublicId(authorId, postBody(hashtags = listOf("산책")))
            createdPublicId(authorId, postBody(hashtags = listOf("산책")))
            val threads = 2
            val ready = CountDownLatch(threads)
            val start = CountDownLatch(1)
            val pool = Executors.newFixedThreadPool(threads)
            val errors = Collections.synchronizedList(mutableListOf<Throwable>())
            try {
                val futures =
                    (1..threads).map {
                        pool.submit {
                            ready.countDown()
                            start.await()
                            try {
                                snsPostDeletionUseCase.deleteByAuthor(authorId, publicId)
                            } catch (e: Throwable) {
                                errors.add(e)
                            }
                        }
                    }
                ready.await(5, TimeUnit.SECONDS)
                start.countDown() // 모든 스레드가 동시에 출발
                futures.forEach { it.get(30, TimeUnit.SECONDS) }
            } finally {
                pool.shutdownNow()
            }

            assertTrue(errors.isEmpty(), "동시 삭제는 둘 다 성공해야 한다: $errors")
            assertEquals(1, statsPostCount(authorId))
            assertEquals(mapOf("산책" to 1), hashtagCounts())
        }

        @Test
        fun `같은 게시물을 동시에 고쳐도 태그 수가 마지막에 남은 태그와 맞는다`() {
            val authorId = onboardedUser()
            val publicId = createdPublicId(authorId, postBody(hashtags = listOf("산책")))
            createdPublicId(authorId, postBody(hashtags = listOf("산책")))
            val updates =
                listOf("한강", "공원").map { tag ->
                    { snsPostUseCase.updatePost(UpdatePostCommand(authorId, publicId, "산책 다녀왔어요", listOf(tag))) }
                }

            val errors = runConcurrently(updates)

            assertTrue(errors.isEmpty(), "같은 게시물 동시 수정은 차례로 모두 성공해야 한다: $errors")
            val last = (postDetail(publicId)["hashtags"] as List<*>).single() as String
            assertEquals(mapOf("산책" to 1, "한강" to 0, "공원" to 0) + (last to 1), hashtagCounts())
        }

        @Test
        fun `같은 게시물 수정은 앞 요청의 잠금이 풀린 뒤 실행하고 나중 요청의 캡션이 남는다`() {
            val authorId = onboardedUser()
            val publicId = createdPublicId(authorId)
            val pool = Executors.newSingleThreadExecutor()
            try {
                val later =
                    tx.execute {
                        // 앞 요청처럼 게시물 행을 잠그고 캡션을 바꾼 채 커밋을 미룬다
                        jdbcTemplate.queryForList("select id from sns_posts where public_id = ? for update", publicId)
                        jdbcTemplate.update("update sns_posts set caption = ? where public_id = ?", "앞 요청 캡션", publicId)
                        // 나중 요청은 원래 캡션으로 되돌린다. 잠그지 않고 읽으면 바뀐 것이 없다고 보고 앞 요청의 캡션이 남는다
                        val future =
                            pool.submit { snsPostUseCase.updatePost(UpdatePostCommand(authorId, publicId, "산책 다녀왔어요", emptyList())) }
                        assertFailsWith<TimeoutException> { future.get(1, TimeUnit.SECONDS) }
                        future
                    }!!
                later.get(30, TimeUnit.SECONDS)
            } finally {
                pool.shutdownNow()
            }

            assertEquals("산책 다녀왔어요", postRow(publicId)["caption"])
            assertNotNull(postRow(publicId)["edited_at"])
        }
    }
}
