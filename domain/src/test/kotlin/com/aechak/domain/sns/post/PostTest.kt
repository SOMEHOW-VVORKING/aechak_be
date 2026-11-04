package com.aechak.domain.sns.post

import com.aechak.common.error.BusinessException
import com.aechak.domain.sns.error.SnsErrorCode
import com.aechak.domain.sns.post.enums.PostDeletionReason
import com.aechak.domain.sns.post.enums.PostMediaKind
import com.aechak.domain.sns.post.enums.PostMediaType
import com.aechak.domain.sns.post.enums.PostStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PostTest {
    private fun aPost(
        caption: String? = "산책 다녀왔어요",
        photoKeys: List<String> = emptyList(),
        hashtagIds: List<Long> = emptyList(),
    ): Post =
        Post.create(
            authorId = 1L,
            caption = caption,
            photoKeys = photoKeys,
            hashtagIds = hashtagIds,
            petId = null,
            idempotencyKey = "key-1",
        )

    private fun photoKeys(count: Int): List<String> = (1..count).map { "sns/posts/images/photo-$it.jpg" }

    @Test
    fun `캡션을 NFC로 정규화하고 줄바꿈을 정리한 뒤 앞뒤 공백을 지운다`() {
        val post = aPost(caption = "  é\r\n산책  ")

        assertEquals("é\n산책", post.caption)
    }

    @Test
    fun `공백만 있는 캡션은 없는 캡션으로 저장한다`() {
        val post = aPost(caption = " \r\n\t ", photoKeys = photoKeys(1))

        assertNull(post.caption)
    }

    @Test
    fun `캡션은 코드 포인트 2,200자까지 쓸 수 있다`() {
        val emojis = "🐶".repeat(2_200)

        assertEquals(emojis, aPost(caption = emojis).caption)
    }

    @Test
    fun `결합 문자는 NFC로 합친 뒤 길이를 센다`() {
        val decomposed = "é".repeat(2_200)

        assertEquals("é".repeat(2_200), aPost(caption = decomposed).caption)
    }

    @Test
    fun `캡션이 코드 포인트 2,200자를 넘으면 130403이다`() {
        val e = assertFailsWith<BusinessException> { aPost(caption = "🐶".repeat(2_201)) }

        assertEquals(SnsErrorCode.SNS_POST_CAPTION_TOO_LONG, e.errorCode)
    }

    @Test
    fun `사진이 10장을 넘으면 130404다`() {
        val e = assertFailsWith<BusinessException> { aPost(photoKeys = photoKeys(11)) }

        assertEquals(SnsErrorCode.SNS_POST_INVALID_MEDIA, e.errorCode)
    }

    @Test
    fun `요청 안에서 같은 키가 두 번 나오면 130404다`() {
        val e = assertFailsWith<BusinessException> { aPost(photoKeys = listOf("a.jpg", "b.jpg", "a.jpg")) }

        assertEquals(SnsErrorCode.SNS_POST_INVALID_MEDIA, e.errorCode)
    }

    @Test
    fun `캡션과 첨부가 모두 없으면 130402다`() {
        val e = assertFailsWith<BusinessException> { aPost(caption = "   ") }

        assertEquals(SnsErrorCode.SNS_POST_EMPTY, e.errorCode)
    }

    @Test
    fun `태그만 있고 캡션과 첨부가 없으면 130402다`() {
        val e = assertFailsWith<BusinessException> { aPost(caption = null, hashtagIds = listOf(1L, 2L)) }

        assertEquals(SnsErrorCode.SNS_POST_EMPTY, e.errorCode)
    }

    @Test
    fun `정규화 결과가 같은 태그는 하나로 합친다`() {
        assertEquals(listOf("dog", "산책"), Post.normalizeHashtags(listOf("#DOG", "산책", "dog", " #Dog ")))
    }

    @Test
    fun `태그는 합친 뒤 30개까지 달 수 있고 31개면 130406이다`() {
        val thirty = (1..30).map { "tag$it" }

        assertEquals(thirty, Post.normalizeHashtags(thirty + "#TAG1"))
        val e = assertFailsWith<BusinessException> { Post.normalizeHashtags(thirty + "tag31") }
        assertEquals(SnsErrorCode.SNS_POST_TOO_MANY_HASHTAGS, e.errorCode)
    }

    @Test
    fun `캡션만 있으면 미디어 없는 텍스트 게시물이다`() {
        val post = aPost()

        assertEquals(PostMediaType.TEXT, post.mediaType)
        assertEquals(PostStatus.PUBLISHED, post.status)
        assertTrue(post.media.isEmpty())
    }

    @Test
    fun `사진은 요청 순서대로 sortOrder가 붙는다`() {
        val keys = listOf("c.jpg", "a.jpg", "b.jpg")

        val post = aPost(caption = null, photoKeys = keys)

        assertEquals(PostMediaType.PHOTO, post.mediaType)
        assertEquals(keys, post.media.map { it.storageKey })
        assertEquals(listOf(0, 1, 2), post.media.map { it.sortOrder })
        assertTrue(post.media.all { it.mediaKind == PostMediaKind.IMAGE })
    }

    @Test
    fun `게시 전 확인은 팩토리와 같은 규칙으로 정규화한 캡션을 돌려준다`() {
        assertEquals("산책", Post.validateDraft("  산책\r\n", emptyList()))

        val e = assertFailsWith<BusinessException> { Post.validateDraft(null, photoKeys(11)) }
        assertEquals(SnsErrorCode.SNS_POST_INVALID_MEDIA, e.errorCode)
    }

    @Test
    fun `삭제하면 상태와 사유와 시각을 남기고 캡션을 지운다`() {
        val post = aPost()

        assertTrue(post.delete(PostDeletionReason.AUTHOR))

        assertEquals(PostStatus.DELETED, post.status)
        assertEquals(PostDeletionReason.AUTHOR, post.deletionReason)
        assertNotNull(post.deletedAt)
        assertNull(post.caption)
    }

    @Test
    fun `이미 삭제된 게시물을 다시 삭제하면 아무것도 바꾸지 않는다`() {
        val post = aPost()
        post.delete(PostDeletionReason.AUTHOR)
        val deletedAt = post.deletedAt

        assertFalse(post.delete(PostDeletionReason.OPERATOR))

        assertEquals(PostStatus.DELETED, post.status)
        assertEquals(PostDeletionReason.AUTHOR, post.deletionReason)
        assertEquals(deletedAt, post.deletedAt)
    }

    @Test
    fun `삭제하면 태그 연결을 비운다`() {
        val post = aPost(hashtagIds = listOf(1L, 2L))

        post.delete(PostDeletionReason.AUTHOR)

        assertTrue(post.hashtags.isEmpty())
    }

    @Test
    fun `같은 캡션으로 고치면 false이고 수정 시각이 그대로다`() {
        val post = aPost(caption = "산책 다녀왔어요")

        assertFalse(post.update("산책 다녀왔어요", emptyList()))

        assertNull(post.editedAt)
    }

    @Test
    fun `다른 캡션으로 고치면 true이고 캡션과 수정 시각이 바뀐다`() {
        val post = aPost(caption = "산책 다녀왔어요")

        assertTrue(post.update("공원 다녀왔어요", emptyList()))

        assertEquals("공원 다녀왔어요", post.caption)
        assertNotNull(post.editedAt)
    }

    @Test
    fun `정규화하면 지금과 같아지는 캡션은 바뀐 것이 없다`() {
        val post = aPost(caption = "산책\n다녀왔어요")

        assertFalse(post.update("  산책\r\n다녀왔어요 ", emptyList()))

        assertNull(post.editedAt)
    }

    @Test
    fun `캡션과 태그가 순서까지 같으면 false이고 수정 시각이 그대로다`() {
        val post = aPost(hashtagIds = listOf(1L, 2L))

        assertFalse(post.update("산책 다녀왔어요", listOf(1L, 2L)))

        assertNull(post.editedAt)
    }

    @Test
    fun `태그 순서만 바꿔도 수정이고 남은 연결은 그대로 둔 채 순서만 고친다`() {
        val post = aPost(hashtagIds = listOf(1L, 2L))
        val links = post.hashtags.toSet()

        assertTrue(post.update("산책 다녀왔어요", listOf(2L, 1L)))

        assertEquals(listOf(2L, 1L), post.hashtags.map { it.hashtagId })
        assertEquals(listOf(0, 1), post.hashtags.map { it.sortOrder })
        assertEquals(links, post.hashtags.toSet())
        assertNotNull(post.editedAt)
    }

    @Test
    fun `태그를 바꾸면 빠진 연결만 지우고 새 태그만 넣는다`() {
        val post = aPost(hashtagIds = listOf(1L, 2L, 3L))
        val kept = post.hashtags.filter { it.hashtagId != 1L }

        assertTrue(post.update("산책 다녀왔어요", listOf(3L, 4L, 2L)))

        assertEquals(listOf(3L, 4L, 2L), post.hashtags.map { it.hashtagId })
        assertTrue(post.hashtags.containsAll(kept))
    }

    @Test
    fun `텍스트 게시물의 캡션을 비우면 130402이고 캡션이 그대로다`() {
        val post = aPost(caption = "산책 다녀왔어요")

        val e = assertFailsWith<BusinessException> { post.update(" ", emptyList()) }

        assertEquals(SnsErrorCode.SNS_POST_EMPTY, e.errorCode)
        assertEquals("산책 다녀왔어요", post.caption)
    }

    @Test
    fun `사진 게시물은 캡션을 지울 수 있고 미디어는 그대로다`() {
        val post = aPost(photoKeys = photoKeys(2))

        assertTrue(post.update(null, emptyList()))

        assertNull(post.caption)
        assertEquals(photoKeys(2), post.media.map { it.storageKey })
    }

    @Test
    fun `고치는 캡션도 코드 포인트 2,200자를 넘으면 130403이다`() {
        val e = assertFailsWith<BusinessException> { aPost().update("🐶".repeat(2_201), emptyList()) }

        assertEquals(SnsErrorCode.SNS_POST_CAPTION_TOO_LONG, e.errorCode)
    }
}
