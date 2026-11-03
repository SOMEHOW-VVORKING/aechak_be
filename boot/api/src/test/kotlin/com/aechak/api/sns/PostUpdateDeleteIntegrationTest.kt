package com.aechak.api.sns

import com.aechak.application.sns.postdeletion.usecase.SnsPostDeletionUseCase
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 통합 — 작성자의 게시물 삭제와 수정이 소유권, 이미 지운 게시물, 수, 파일 정리를 계약대로 처리하는지.
 */
class PostUpdateDeleteIntegrationTest : SnsPostIntegrationTestBase() {
    @Autowired
    private lateinit var snsPostDeletionUseCase: SnsPostDeletionUseCase

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

    @Nested
    inner class Concurrency {
        @Test
        fun `같은 게시물을 동시에 두 번 지워도 수는 한 번만 준다`() {
            val authorId = onboardedUser()
            val publicId = createdPublicId(authorId)
            createdPublicId(authorId)
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
        }
    }
}
