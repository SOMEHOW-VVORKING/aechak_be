package com.aechak.domain.sns.post

import com.aechak.domain.sns.post.enums.PostMediaKind
import com.aechak.domain.support.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/** 게시물에 붙은 사진이나 영상 하나. 게시물과 함께만 만들어진다 */
@Entity
@Table(
    name = "sns_post_media",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_sns_post_media_post_sort", columnNames = ["post_id", "sort_order"]),
        UniqueConstraint(name = PostMedia.UK_STORAGE_KEY, columnNames = ["storage_key"]),
    ],
)
class PostMedia protected constructor(
    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, updatable = false, length = 10)
    val mediaKind: PostMediaKind,
    @Column(nullable = false, updatable = false, length = 512)
    val storageKey: String,
    @Column(nullable = false, updatable = false)
    val sortOrder: Int,
    @Column(updatable = false, length = 512)
    val thumbnailKey: String?,
    @Column(updatable = false)
    val durationMs: Int?,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    companion object {
        const val UK_STORAGE_KEY = "uk_sns_post_media_storage_key"

        fun image(
            storageKey: String,
            sortOrder: Int,
        ): PostMedia = PostMedia(PostMediaKind.IMAGE, storageKey, sortOrder, thumbnailKey = null, durationMs = null)

        fun video(
            storageKey: String,
            thumbnailKey: String?,
            durationMs: Int,
        ): PostMedia = PostMedia(PostMediaKind.VIDEO, storageKey, sortOrder = 0, thumbnailKey = thumbnailKey, durationMs = durationMs)
    }
}
