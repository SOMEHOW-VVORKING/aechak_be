package com.aechak.domain.sns.like

import com.aechak.domain.support.AggregateRoot
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/** user가 post에 누른 좋아요. 행은 저장소 원자 삽입과 삭제로만 바꾼다 */
@Entity
@Table(
    name = "sns_post_likes",
    uniqueConstraints = [UniqueConstraint(name = "uk_sns_post_likes_post_user", columnNames = ["post_id", "user_id"])],
    indexes = [Index(name = "ix_sns_post_likes_post_recent", columnList = "post_id, id, user_id")],
)
class PostLike protected constructor(
    @Column(name = "post_id", nullable = false)
    val postId: Long,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
) : AggregateRoot() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L
}
