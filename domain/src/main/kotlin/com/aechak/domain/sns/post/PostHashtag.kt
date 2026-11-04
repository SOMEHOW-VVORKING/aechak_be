package com.aechak.domain.sns.post

import com.aechak.domain.support.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/** 게시물에 단 해시태그 하나. 태그 행은 다른 애그리거트라 연관 없이 id 값만 들고 작성자가 단 순서를 남긴다 */
@Entity
@Table(
    name = "sns_post_hashtags",
    uniqueConstraints = [UniqueConstraint(name = "uk_sns_post_hashtags_post_hashtag", columnNames = ["post_id", "hashtag_id"])],
)
class PostHashtag protected constructor(
    @Column(nullable = false, updatable = false)
    val hashtagId: Long,
    @Column(nullable = false)
    val sortOrder: Int,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    companion object {
        fun of(
            hashtagId: Long,
            sortOrder: Int,
        ): PostHashtag = PostHashtag(hashtagId, sortOrder)
    }
}
