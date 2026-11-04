package com.aechak.domain.sns.post

import com.aechak.common.error.BusinessException
import com.aechak.domain.sns.error.SnsErrorCode
import com.aechak.domain.sns.post.enums.PostDeletionReason
import com.aechak.domain.sns.post.enums.PostMediaType
import com.aechak.domain.sns.post.enums.PostStatus
import com.aechak.domain.support.AggregateRoot
import com.aechak.domain.support.Ulid
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.text.Normalizer
import java.time.LocalDateTime

/** SNS 게시물. 캡션은 금지어를 가린 뒤의 글이고 게시물을 지우면 상태만 남기고 캡션을 비운다 */
@Entity
@Table(
    name = "sns_posts",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_sns_posts_public_id", columnNames = ["public_id"]),
        UniqueConstraint(name = Post.UK_AUTHOR_IDEMPOTENCY, columnNames = ["author_id", "idempotency_key"]),
    ],
)
class Post protected constructor(
    @Column(nullable = false, updatable = false)
    val authorId: Long,
    caption: String?,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    val mediaType: PostMediaType,
    @Column(updatable = false)
    val petId: Long?,
    // 바이트 그대로 비교한다. 기본 콜레이션이면 대소문자만 다른 키가 같은 게시물로 묶인다
    @Column(nullable = false, updatable = false, columnDefinition = "varchar($IDEMPOTENCY_KEY_MAX_LENGTH) collate utf8mb4_bin")
    val idempotencyKey: String,
) : AggregateRoot() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Column(nullable = false, updatable = false, length = 26)
    val publicId: String = Ulid.generate()

    @Column(length = CAPTION_MAX_CODE_POINTS)
    var caption: String? = caption
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: PostStatus = PostStatus.PUBLISHED
        protected set

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    var deletionReason: PostDeletionReason? = null
        protected set

    var editedAt: LocalDateTime? = null
        protected set

    var deletedAt: LocalDateTime? = null
        protected set

    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "post_id", nullable = false, updatable = false)
    @OrderBy("sortOrder ASC")
    private val _media: MutableList<PostMedia> = mutableListOf()
    val media: List<PostMedia> get() = _media.toList()

    /** 캡션을 통째로 바꾼다. 정규화한 캡션이 지금과 같으면 아무것도 바꾸지 않고 false를 돌려준다 */
    fun update(caption: String?): Boolean {
        val normalized = normalizeCaption(caption)
        if (normalized == this.caption) return false
        if (normalized == null && mediaType == PostMediaType.TEXT) throw BusinessException(SnsErrorCode.SNS_POST_EMPTY)
        this.caption = normalized
        editedAt = LocalDateTime.now()
        return true
    }

    /** 게시물을 지운다. 이미 지운 게시물이면 아무것도 바꾸지 않고 false를 돌려준다 */
    fun delete(reason: PostDeletionReason): Boolean {
        if (status == PostStatus.DELETED) return false
        status = PostStatus.DELETED
        deletionReason = reason
        deletedAt = LocalDateTime.now()
        caption = null
        return true
    }

    companion object {
        const val UK_AUTHOR_IDEMPOTENCY = "uk_sns_posts_author_idempotency"
        const val CAPTION_MAX_CODE_POINTS = 2_200
        const val MAX_PHOTOS = 10
        const val IDEMPOTENCY_KEY_MAX_LENGTH = 100

        fun create(
            authorId: Long,
            caption: String?,
            photoKeys: List<String>,
            petId: Long?,
            idempotencyKey: String,
        ): Post {
            val normalized = validateDraft(caption, photoKeys)
            val mediaType = if (photoKeys.isEmpty()) PostMediaType.TEXT else PostMediaType.PHOTO
            val post = Post(authorId, normalized, mediaType, petId, idempotencyKey)
            photoKeys.forEachIndexed { index, key -> post._media += PostMedia.image(key, index) }
            return post
        }

        /** 게시 규칙을 확인하고 정규화한 캡션을 돌려준다. 승격 전에 같은 규칙으로 미리 확인할 때도 쓴다 */
        fun validateDraft(
            caption: String?,
            photoKeys: List<String>,
        ): String? {
            val normalized = normalizeCaption(caption)
            if (normalized == null && photoKeys.isEmpty()) throw BusinessException(SnsErrorCode.SNS_POST_EMPTY)
            if (photoKeys.size > MAX_PHOTOS || photoKeys.toSet().size != photoKeys.size) {
                throw BusinessException(SnsErrorCode.SNS_POST_INVALID_MEDIA)
            }
            return normalized
        }

        /** NFC로 정규화하고 줄바꿈을 \n으로 맞춘 뒤 앞뒤 공백을 지운다. 남은 글이 없으면 캡션이 없는 것이다 */
        fun normalizeCaption(caption: String?): String? {
            val normalized = caption?.let { Normalizer.normalize(it, Normalizer.Form.NFC).replace("\r\n", "\n").trim() }
            if (normalized.isNullOrBlank()) return null
            if (normalized.codePointCount(0, normalized.length) > CAPTION_MAX_CODE_POINTS) {
                throw BusinessException(SnsErrorCode.SNS_POST_CAPTION_TOO_LONG)
            }
            return normalized
        }
    }
}
