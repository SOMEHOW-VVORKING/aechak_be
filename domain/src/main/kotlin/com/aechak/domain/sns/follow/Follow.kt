package com.aechak.domain.sns.follow

import com.aechak.domain.support.AggregateRoot
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/** follower가 followee를 팔로우한 관계. 행은 저장소 원자 삽입과 삭제로만 바꾼다 */
@Entity
@Table(
    name = "sns_follows",
    uniqueConstraints = [UniqueConstraint(name = "uk_sns_follow_pair", columnNames = ["follower_id", "followee_id"])],
    indexes = [
        Index(name = "ix_sns_follow_followee", columnList = "followee_id, id, follower_id"),
        Index(name = "ix_sns_follow_follower", columnList = "follower_id, id, followee_id"),
    ],
)
class Follow protected constructor(
    @Column(name = "follower_id", nullable = false)
    val followerId: Long,
    @Column(name = "followee_id", nullable = false)
    val followeeId: Long,
) : AggregateRoot() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L
}
