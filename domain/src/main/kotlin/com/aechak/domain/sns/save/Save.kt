package com.aechak.domain.sns.save

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
    name = "sns_saves",
    uniqueConstraints = [UniqueConstraint(name = "uk_sns_saves_user_post", columnNames = ["user_id", "post_id"])],
    indexes = [Index(name = "ix_sns_saves_user", columnList = "user_id, id, post_id")],
)
class Save protected constructor(
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "post_id", nullable = false)
    val postId: Long,
) : AggregateRoot() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L
}
