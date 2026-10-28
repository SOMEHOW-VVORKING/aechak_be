package com.aechak.domain.sns.block

import com.aechak.domain.support.AggregateRoot
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/** blocker가 blocked를 차단한 관계. 행은 저장소 원자 삽입과 삭제로만 바꾼다 */
@Entity
@Table(
    name = "sns_blocks",
    uniqueConstraints = [UniqueConstraint(name = "uk_sns_block_pair", columnNames = ["blocker_id", "blocked_id"])],
    indexes = [
        Index(name = "ix_sns_block_blocker", columnList = "blocker_id, id"),
        Index(name = "ix_sns_block_blocked", columnList = "blocked_id, blocker_id"),
    ],
)
class Block protected constructor(
    @Column(name = "blocker_id", nullable = false)
    val blockerId: Long,
    @Column(name = "blocked_id", nullable = false)
    val blockedId: Long,
) : AggregateRoot() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L
}
