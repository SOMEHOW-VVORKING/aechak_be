package com.aechak.api.sns

import com.aechak.api.support.CommitFailureInjector
import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.application.sns.postdeletion.usecase.SnsPostDeletionUseCase
import com.aechak.application.sns.usecase.SnsPostUseCase
import com.aechak.application.sns.usecase.command.CreatePostCommand
import com.aechak.application.sns.usecase.command.UpdatePostCommand
import com.aechak.domain.support.Ulid
import org.hamcrest.Matchers.hasLength
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
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 통합 — 게시가 첨부 규칙, 키 승격, 멱등키, 작성자 게시물 수를 계약대로 처리하는지.
 */
class PostCreateIntegrationTest : SnsPostIntegrationTestBase() {
    @Autowired
    private lateinit var snsPostUseCase: SnsPostUseCase

    @Autowired
    private lateinit var snsPostDeletionUseCase: SnsPostDeletionUseCase

    @Autowired
    private lateinit var commitFailureInjector: CommitFailureInjector

    @Test
    fun `사진 한 장을 게시하면 승격한 키로 미디어 행을 남긴다`() {
        val userId = onboardedUser()
        val tmpKey = tmpPhotoKey(userId)

        val publicId = createdPublicId(userId, postBody(photoKeys = listOf(tmpKey)))

        assertEquals("PHOTO", postRow(publicId)["media_type"])
        assertEquals("PUBLISHED", postRow(publicId)["status"])
        assertEquals(listOf(promotedKeyOf(tmpKey)), mediaKeysOf(publicId))
    }

    @Test
    fun `사진 10장은 요청 순서대로 sort_order에 남는다`() {
        val userId = onboardedUser()
        val tmpKeys = List(10) { tmpPhotoKey(userId) }.shuffled()

        val publicId = createdPublicId(userId, postBody(caption = null, photoKeys = tmpKeys))

        assertEquals(tmpKeys.map(::promotedKeyOf), mediaKeysOf(publicId))
    }

    @Test
    fun `캡션만 있는 게시물은 미디어 없는 TEXT다`() {
        val userId = onboardedUser()

        val publicId = createdPublicId(userId, postBody(caption = "산책 코스 추천받아요"))

        assertEquals("TEXT", postRow(publicId)["media_type"])
        assertEquals("산책 코스 추천받아요", postRow(publicId)["caption"])
        assertTrue(mediaKeysOf(publicId).isEmpty())
    }

    @Test
    fun `대표 펫이 있으면 그 펫을 게시물에 남긴다`() {
        val userId = onboardedUser()
        val petId = defaultPet(userId)

        val publicId = createdPublicId(userId)

        assertEquals(petId, (postRow(publicId)["pet_id"] as Number).toLong())
    }

    @Test
    fun `펫을 등록하지 않은 작성자의 게시물은 펫이 없다`() {
        val userId = onboardedUser()

        val publicId = createdPublicId(userId)

        assertNull(postRow(publicId)["pet_id"])
    }

    @Test
    fun `게시하면 작성자 게시물 수가 1 오른다`() {
        val userId = onboardedUser()

        createdPublicId(userId)

        assertEquals(1, statsPostCount(userId))
    }

    @Test
    fun `사진이 10장을 넘으면 130404이고 승격하지 않는다`() {
        val userId = onboardedUser()

        createPost(userId, postBody(photoKeys = List(11) { tmpPhotoKey(userId) }))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(130404))

        assertTrue(fakeFileStorage.promotedKeys.isEmpty())
        assertEquals(0, postCountOf(userId))
    }

    @Test
    fun `캡션과 첨부가 모두 없으면 130402다`() {
        val userId = onboardedUser()

        createPost(userId, postBody(caption = "   "))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(130402))
    }

    @Test
    fun `캡션이 2,200자를 넘으면 130403이다`() {
        val userId = onboardedUser()

        createPost(userId, postBody(caption = "가".repeat(2_201)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(130403))
    }

    @Test
    fun `남이 발급받은 키는 403 100002다`() {
        val userId = onboardedUser()
        val otherId = onboardedUser()

        createPost(userId, postBody(photoKeys = listOf(tmpPhotoKey(otherId))))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(100002))
        assertEquals(0, postCountOf(userId))
    }

    @Test
    fun `다른 용도로 발급받은 키는 400 100003이다`() {
        val userId = onboardedUser()

        createPost(userId, postBody(photoKeys = listOf(tmpPhotoKey(userId, UploadPurpose.REVIEW_IMAGE))))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(100003))
    }

    @Test
    fun `같은 키로 다시 보내면 같은 게시물이고 다시 승격하지 않는다`() {
        val userId = onboardedUser()
        val key = Ulid.generate()
        val body = postBody(photoKeys = listOf(tmpPhotoKey(userId)))

        val first = createdPublicId(userId, body, key)
        val promotedAfterFirst = fakeFileStorage.promotedKeys.size
        val second = createdPublicId(userId, body, key)

        assertEquals(first, second)
        assertEquals(1, postCountOf(userId))
        assertEquals(promotedAfterFirst, fakeFileStorage.promotedKeys.size)
        assertEquals(1, statsPostCount(userId))
    }

    @Test
    fun `게시 뒤 승격에 쓴 tmp 키를 지우고 그 키를 다른 게시에 보내면 404 100001이다`() {
        val userId = onboardedUser()
        val tmpKey = tmpPhotoKey(userId)

        createdPublicId(userId, postBody(photoKeys = listOf(tmpKey)))

        assertTrue(tmpKey in fakeFileStorage.deletedKeys)
        createPost(userId, postBody(photoKeys = listOf(tmpKey)))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(100001))
    }

    @Test
    fun `tmp 키 삭제가 실패해 같은 tmp 키로 다시 게시돼도 두 게시물은 서로 다른 파일을 쓴다`() {
        val userId = onboardedUser()
        val tmpKey = tmpPhotoKey(userId)
        fakeFileStorage.failDeletesOf(setOf(tmpKey))

        val first = createdPublicId(userId, postBody(photoKeys = listOf(tmpKey)))
        val second = createdPublicId(userId, postBody(photoKeys = listOf(tmpKey)))

        assertNotEquals(mediaKeysOf(first).single(), mediaKeysOf(second).single())
    }

    @Test
    fun `커밋 응답을 받지 못해도 저장된 게시물의 사진을 지우지 않고 같은 키로 다시 보내면 그 게시물을 돌려준다`() {
        val userId = onboardedUser()
        val command = CreatePostCommand(userId, Ulid.generate(), "응답 유실", listOf(tmpPhotoKey(userId)))

        assertFailsWith<IllegalStateException> {
            commitFailureInjector.failAfterFirstWriteCommit { snsPostUseCase.createPost(command) }
        }
        val publicId = snsPostUseCase.createPost(command).publicId

        val photoKey = mediaKeysOf(publicId).single()
        assertTrue(photoKey !in fakeFileStorage.deletedKeys)
        assertEquals(listOf(photoKey), fakeFileStorage.promotedKeys)
        assertEquals(1, postCountOf(userId))
    }

    @Test
    fun `두 번째 사진 승격이 실패하면 먼저 승격한 파일을 지우고 tmp 키는 남긴다`() {
        val userId = onboardedUser()
        val ownKey = tmpPhotoKey(userId)

        createPost(userId, postBody(photoKeys = listOf(ownKey, tmpPhotoKey(onboardedUser()))))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(100002))

        assertEquals(1, fakeFileStorage.promotedKeys.size)
        assertEquals(fakeFileStorage.promotedKeys, fakeFileStorage.deletedKeys)
        assertEquals(0, postCountOf(userId))
    }

    @Test
    fun `대소문자만 다른 멱등키는 서로 다른 게시물이다`() {
        val userId = onboardedUser()

        val first = createdPublicId(userId, postBody(caption = "첫 글"), idempotencyKey = "draft-A")
        val second = createdPublicId(userId, postBody(caption = "둘째 글"), idempotencyKey = "draft-a")

        assertNotEquals(first, second)
        assertEquals(2, postCountOf(userId))
    }

    @Test
    fun `Idempotency-Key 헤더가 없으면 90001이다`() {
        val userId = onboardedUser()

        createPost(userId, postBody(), idempotencyKey = null)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
    }

    @Test
    fun `빈 키와 100자를 넘는 키는 90004다`() {
        val userId = onboardedUser()

        createPost(userId, postBody(), idempotencyKey = " ")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90004))
        createPost(userId, postBody(), idempotencyKey = "k".repeat(101))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90004))
    }

    @Test
    fun `영상은 영상 용도로 썸네일은 사진 용도로 승격해 미디어 행 하나에 남긴다`() {
        val userId = onboardedUser()
        val videoKey = tmpVideoKey(userId)
        val thumbnailKey = tmpPhotoKey(userId)

        val publicId = createdPublicId(userId, postBody(caption = null, video = videoJson(videoKey, thumbnailKey)))

        assertEquals("VIDEO", postRow(publicId)["media_type"])
        val media =
            jdbcTemplate.queryForMap(
                "select m.* from sns_post_media m join sns_posts p on p.id = m.post_id where p.public_id = ?",
                publicId,
            )
        assertEquals("VIDEO", media["media_type"])
        assertEquals(promotedKeyOf(videoKey), media["storage_key"])
        assertEquals(promotedKeyOf(thumbnailKey), media["thumbnail_key"])
        assertEquals(42_500, (media["duration_ms"] as Number).toInt())
        assertTrue(promotedKeyOf(videoKey).startsWith("sns/posts/videos/"))
        assertTrue(promotedKeyOf(thumbnailKey).startsWith("sns/posts/images/"))
    }

    @Test
    fun `영상 게시가 커밋되면 영상과 썸네일의 tmp 키를 지운다`() {
        val userId = onboardedUser()
        val videoKey = tmpVideoKey(userId)
        val thumbnailKey = tmpPhotoKey(userId)

        createdPublicId(userId, postBody(caption = null, video = videoJson(videoKey, thumbnailKey)))

        assertTrue(fakeFileStorage.deletedKeys.containsAll(listOf(videoKey, thumbnailKey)))
    }

    @Test
    fun `영상 키를 사진 용도로 발급받은 키로 보내면 400 100003이다`() {
        val userId = onboardedUser()

        createPost(userId, postBody(caption = null, video = videoJson(tmpPhotoKey(userId), tmpPhotoKey(userId))))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(100003))
        assertEquals(0, postCountOf(userId))
    }

    @Test
    fun `썸네일 키를 영상 용도로 발급받은 키로 보내면 400 100003이다`() {
        val userId = onboardedUser()

        createPost(userId, postBody(caption = null, video = videoJson(tmpVideoKey(userId), tmpVideoKey(userId))))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(100003))
        assertEquals(0, postCountOf(userId))
    }

    @Test
    fun `썸네일 승격이 실패하면 먼저 승격한 영상 파일을 지운다`() {
        val userId = onboardedUser()

        createPost(userId, postBody(caption = null, video = videoJson(tmpVideoKey(userId), tmpPhotoKey(onboardedUser()))))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(100002))

        assertEquals(1, fakeFileStorage.promotedKeys.size)
        assertEquals(fakeFileStorage.promotedKeys, fakeFileStorage.deletedKeys)
        assertEquals(0, postCountOf(userId))
    }

    @Test
    fun `영상이 60초를 넘으면 130407이고 승격하지 않는다`() {
        val userId = onboardedUser()

        createPost(userId, postBody(caption = null, video = videoJson(tmpVideoKey(userId), tmpPhotoKey(userId), durationMs = 60_001)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(130407))
        assertTrue(fakeFileStorage.promotedKeys.isEmpty())
    }

    @Test
    fun `영상에 썸네일 키가 빠지면 90001이 아니라 130404다`() {
        val userId = onboardedUser()

        createPost(userId, postBody(caption = null, video = """{"key":"${tmpVideoKey(userId)}","durationMs":42500}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(130404))
    }

    @Test
    fun `영상 길이가 1보다 작으면 90001이다`() {
        val userId = onboardedUser()

        createPost(userId, postBody(caption = null, video = videoJson(tmpVideoKey(userId), tmpPhotoKey(userId), durationMs = 0)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
    }

    @Test
    fun `영상 두 개는 요청 형식상 받을 수 없어 90001이다`() {
        val userId = onboardedUser()
        val video = videoJson(tmpVideoKey(userId), tmpPhotoKey(userId))

        createPost(userId, postBody(caption = null, video = "[$video,$video]"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
        assertEquals(0, postCountOf(userId))
    }

    @Test
    fun `응답에는 publicId만 있다`() {
        val userId = onboardedUser()

        createPost(userId, postBody())
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data.publicId", hasLength(26)))
    }

    @Nested
    inner class Concurrency {
        @Test
        fun `같은 키 게시를 동시에 보내도 게시물 하나와 게시물 수 1만 남는다`() {
            val userId = onboardedUser()
            val command = CreatePostCommand(userId, Ulid.generate(), "버튼 연타", listOf(tmpPhotoKey(userId)))
            val threads = 6
            val ready = CountDownLatch(threads)
            val start = CountDownLatch(1)
            val pool = Executors.newFixedThreadPool(threads)
            val publicIds = Collections.synchronizedList(mutableListOf<String>())
            val errors = Collections.synchronizedList(mutableListOf<Throwable>())
            try {
                val futures =
                    (1..threads).map {
                        pool.submit {
                            ready.countDown()
                            start.await()
                            try {
                                publicIds += snsPostUseCase.createPost(command).publicId
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

            assertTrue(errors.isEmpty(), "같은 키 동시 게시는 오류 없이 같은 게시물을 돌려줘야 한다: $errors")
            assertEquals(1, publicIds.toSet().size)
            assertEquals(1, postCountOf(userId))
            assertEquals(1, statsPostCount(userId))
            val kept = mediaKeysOf(publicIds.first())
            assertTrue(kept.none { it in fakeFileStorage.deletedKeys })
            assertTrue(
                fakeFileStorage.deletedKeys.containsAll(fakeFileStorage.promotedKeys - kept.toSet()),
                "게시하지 못한 요청이 승격한 파일은 지워야 한다",
            )
        }

        @Test
        fun `같은 새 태그를 서로 다른 순서로 단 게시를 동시에 보내도 교착 없이 태그 행이 하나씩이고 수가 맞다`() {
            val tags = listOf("산책", "댕댕이", "골든리트리버", "ㅋㅋㅋ")
            val threads = 6
            // 짝수 번째와 홀수 번째 요청이 서로 반대 순서로 단다
            val commands =
                List(threads) { i ->
                    val order = if (i % 2 == 0) tags else tags.reversed()
                    CreatePostCommand(onboardedUser(), Ulid.generate(), "동시 게시", emptyList(), order)
                }
            val ready = CountDownLatch(threads)
            val start = CountDownLatch(1)
            val pool = Executors.newFixedThreadPool(threads)
            val errors = Collections.synchronizedList(mutableListOf<Throwable>())
            try {
                val futures =
                    commands.map { command ->
                        pool.submit {
                            ready.countDown()
                            start.await()
                            try {
                                snsPostUseCase.createPost(command)
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

            assertTrue(errors.isEmpty(), "태그 순서가 달라도 동시 게시는 교착 없이 모두 성공해야 한다: $errors")
            assertEquals(tags.associateWith { threads }, hashtagCounts())
        }

        @Test
        fun `두 게시물에서 서로 반대로 태그를 바꾸는 수정을 동시에 보내도 교착 없이 수가 맞다`() {
            // 태그 id 순서와 이름 순서가 반대가 되도록 b를 먼저 만든다
            val bToA = List(4) { onboardedUser().let { it to createdPublicId(it, postBody(hashtags = listOf("b"))) } }
            val aToB = List(2) { onboardedUser().let { it to createdPublicId(it, postBody(hashtags = listOf("a"))) } }
            val commands =
                bToA.map { (userId, publicId) -> UpdatePostCommand(userId, publicId, "동시 수정", listOf("a")) } +
                    aToB.map { (userId, publicId) -> UpdatePostCommand(userId, publicId, "동시 수정", listOf("b")) }

            val errors = runConcurrently(commands.map { command -> { snsPostUseCase.updatePost(command) } })

            assertTrue(errors.isEmpty(), "태그를 서로 반대로 바꾸는 동시 수정은 교착 없이 모두 성공해야 한다: $errors")
            assertEquals(mapOf("a" to 4, "b" to 2), hashtagCounts())
        }

        @Test
        fun `태그를 단 게시와 같은 태그가 달린 다른 게시물의 삭제가 겹쳐도 교착 없이 수가 맞다`() {
            val authorId = onboardedUser()
            // 태그 id 순서와 이름 순서가 반대가 되도록 b를 먼저 만든다
            createdPublicId(authorId, postBody(hashtags = listOf("b")))
            val targets = List(4) { createdPublicId(authorId, postBody(hashtags = listOf("a", "b"))) }
            val deletes = targets.map { publicId -> { snsPostDeletionUseCase.deleteByAuthor(authorId, publicId) } }
            val creates: List<() -> Unit> =
                List(2) {
                    { snsPostUseCase.createPost(CreatePostCommand(authorId, Ulid.generate(), "동시 게시", emptyList(), listOf("a", "b"))) }
                }

            val errors = runConcurrently(deletes + creates)

            assertTrue(errors.isEmpty(), "같은 태그의 게시와 삭제가 겹쳐도 교착 없이 모두 성공해야 한다: $errors")
            assertEquals(mapOf("a" to 2, "b" to 3), hashtagCounts())
            assertEquals(3, statsPostCount(authorId))
        }
    }
}
