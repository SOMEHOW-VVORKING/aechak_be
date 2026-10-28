package com.aechak.domain.review.reply

import com.aechak.common.error.BusinessException
import com.aechak.domain.review.error.ReviewErrorCode
import com.aechak.domain.support.AggregateRoot
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/** 셀러가 자기 상품에 달린 리뷰에 남기는 답글. 리뷰 하나당 최대 하나다(UNIQUE). */
@Entity
@Table(
    name = "review_replies",
    uniqueConstraints = [UniqueConstraint(name = ReviewReply.UK_REVIEW_ID, columnNames = ["review_id"])],
    // V27 규약대로 BC 밖 참조(seller_id)에 인덱스만 둔다. 이름을 V59와 맞춰야 ddl-auto=update인
    // 개발·테스트 스키마가 운영과 같은 모양이 된다.
    indexes = [Index(name = "ix_review_replies_seller_id", columnList = "seller_id")],
)
class ReviewReply protected constructor(
    val reviewId: Long,
    val sellerId: Long,
    content: String,
) : AggregateRoot() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Column(nullable = false, length = CONTENT_MAX)
    var content: String = content
        protected set

    fun edit(content: String) {
        this.content = validateContent(content)
    }

    companion object {
        const val UK_REVIEW_ID = "uk_review_replies_review_id"

        const val CONTENT_MAX = 500

        fun write(
            reviewId: Long,
            sellerId: Long,
            content: String,
        ): ReviewReply = ReviewReply(reviewId, sellerId, validateContent(content))

        private fun validateContent(content: String): String {
            val trimmed = content.trim()
            if (trimmed.isEmpty() || trimmed.length > CONTENT_MAX) {
                throw BusinessException(ReviewErrorCode.INVALID_REVIEW_REPLY_CONTENT)
            }
            return trimmed
        }
    }
}
