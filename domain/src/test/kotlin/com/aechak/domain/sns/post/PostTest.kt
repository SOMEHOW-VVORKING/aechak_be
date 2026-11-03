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
    ): Post = Post.create(authorId = 1L, caption = caption, photoKeys = photoKeys, petId = null, idempotencyKey = "key-1")

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
}
