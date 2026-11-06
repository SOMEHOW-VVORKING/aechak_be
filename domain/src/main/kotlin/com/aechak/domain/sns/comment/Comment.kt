package com.aechak.domain.sns.comment

import com.aechak.common.error.BusinessException
import com.aechak.domain.sns.comment.enums.CommentDeletionReason
import com.aechak.domain.sns.comment.enums.CommentStatus
import com.aechak.domain.sns.error.SnsErrorCode
import com.aechak.domain.sns.post.Post
import com.aechak.domain.support.AggregateRoot
import com.aechak.domain.support.Ulid
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.text.Normalizer
import java.time.LocalDateTime

/** SNS 댓글. 대댓글은 최상위 댓글을 parent로 가리키고 지운 댓글도 행을 남긴다 */
@Entity
@Table(
    name = "sns_comments",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_sns_comments_public_id", columnNames = ["public_id"]),
        UniqueConstraint(name = Comment.UK_AUTHOR_IDEMPOTENCY, columnNames = ["author_id", "post_id", "idempotency_key"]),
    ],
    indexes = [
        // 최상위 댓글 keyset과 게시물별 수. 끝의 status, author_id로 클러스터드 인덱스를 다시 읽지 않는다
        Index(name = "ix_sns_comments_post_parent", columnList = "post_id, parent_id, id, status, author_id"),
        // 대댓글과 안내 문구 판정
        Index(name = "ix_sns_comments_parent", columnList = "parent_id, id, status, author_id"),
    ],
)
class Comment protected constructor(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false, updatable = false, foreignKey = ForeignKey(name = "fk_sns_comments_post"))
    val post: Post,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", updatable = false, foreignKey = ForeignKey(name = "fk_sns_comments_parent"))
    val parent: Comment?,
    @Column(nullable = false, updatable = false)
    val authorId: Long,
    content: String,
    // 바이트 그대로 비교한다. 기본 콜레이션이면 대소문자만 다른 키가 같은 댓글로 묶인다
    @Column(nullable = false, updatable = false, columnDefinition = "varchar($IDEMPOTENCY_KEY_MAX_LENGTH) collate utf8mb4_bin")
    val idempotencyKey: String,
) : AggregateRoot() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Column(nullable = false, updatable = false, length = 26)
    val publicId: String = Ulid.generate()

    @Column(length = CONTENT_MAX_CODE_POINTS)
    var content: String? = content
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: CommentStatus = CommentStatus.ACTIVE
        protected set

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    var deletionReason: CommentDeletionReason? = null
        protected set

    var editedAt: LocalDateTime? = null
        protected set

    var deletedAt: LocalDateTime? = null
        protected set

    /** 내용을 바꾼다. 정규화한 내용이 지금과 같으면 아무것도 바꾸지 않고 false를 돌려준다 */
    fun edit(content: String): Boolean {
        val normalized = normalizeContent(content)
        if (normalized == this.content) return false
        this.content = normalized
        editedAt = LocalDateTime.now()
        return true
    }

    /** 댓글을 지운다. 작성자와 운영자 삭제는 내용을 비운다. 이미 지운 댓글이면 아무것도 바꾸지 않고 false를 돌려준다 */
    fun delete(reason: CommentDeletionReason): Boolean {
        if (status == CommentStatus.DELETED) return false
        status = CommentStatus.DELETED
        deletionReason = reason
        deletedAt = LocalDateTime.now()
        if (reason != CommentDeletionReason.WITHDRAWAL) content = null
        return true
    }

    companion object {
        const val UK_AUTHOR_IDEMPOTENCY = "uk_sns_comments_author_idempotency"
        const val CONTENT_MAX_CODE_POINTS = 500
        const val IDEMPOTENCY_KEY_MAX_LENGTH = 100

        /** 댓글을 만든다. parent는 최상위 댓글이어야 하고 대댓글에 단 답글은 서비스가 최상위 댓글로 바꿔 넘긴다 */
        fun write(
            post: Post,
            parent: Comment?,
            authorId: Long,
            content: String,
            idempotencyKey: String,
        ): Comment {
            require(parent?.parent == null) { "parent must be a root comment." }
            return Comment(post, parent, authorId, normalizeContent(content), idempotencyKey)
        }

        /** NFC로 정규화하고 줄바꿈을 \n으로 맞춘 뒤 앞뒤 공백을 지운다. 비면 130702, 코드 포인트 500자를 넘으면 130703이다 */
        fun normalizeContent(raw: String): String {
            val normalized = Normalizer.normalize(raw, Normalizer.Form.NFC).replace("\r\n", "\n").trim()
            if (normalized.isEmpty()) throw BusinessException(SnsErrorCode.SNS_COMMENT_EMPTY)
            if (normalized.codePointCount(0, normalized.length) > CONTENT_MAX_CODE_POINTS) {
                throw BusinessException(SnsErrorCode.SNS_COMMENT_TOO_LONG)
            }
            return normalized
        }
    }
}
