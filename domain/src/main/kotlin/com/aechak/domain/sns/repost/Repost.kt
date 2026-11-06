package com.aechak.domain.sns.repost

import com.aechak.domain.support.AggregateRoot
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/** 행은 저장소 원자 삽입과 삭제로만 바꾼다 */
@Entity
@Table(
    name = "sns_reposts",
    uniqueConstraints = [UniqueConstraint(name = "uk_sns_reposts_reposter_post", columnNames = ["reposter_id", "post_id"])],
    indexes = [
        Index(name = "ix_sns_reposts_reposter", columnList = "reposter_id, id, post_id, created_at"),
        Index(name = "ix_sns_reposts_post", columnList = "post_id, reposter_id, created_at"),
    ],
)
class Repost protected constructor(
    @Column(name = "post_id", nullable = false)
    val postId: Long,
    @Column(name = "reposter_id", nullable = false)
    val reposterId: Long,
) : AggregateRoot() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L
}
