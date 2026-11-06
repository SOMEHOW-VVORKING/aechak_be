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

/** user가 comment에 누른 좋아요. 행은 저장소 원자 삽입과 삭제로만 바꾼다 */
@Entity
@Table(
    name = "sns_comment_likes",
    uniqueConstraints = [UniqueConstraint(name = "uk_sns_comment_likes_comment_user", columnNames = ["comment_id", "user_id"])],
    indexes = [Index(name = "ix_sns_comment_likes_comment_recent", columnList = "comment_id, id, user_id")],
)
class CommentLike protected constructor(
    @Column(name = "comment_id", nullable = false)
    val commentId: Long,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
) : AggregateRoot() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L
}
