package com.aechak.domain.sns.mute

import com.aechak.domain.support.AggregateRoot
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/** muter가 muted를 뮤트한 관계. 행은 저장소 원자 삽입과 삭제로만 바꾼다 */
@Entity
@Table(
    name = "sns_mutes",
    uniqueConstraints = [UniqueConstraint(name = "uk_sns_mute_pair", columnNames = ["muter_id", "muted_id"])],
    indexes = [Index(name = "ix_sns_mute_muter", columnList = "muter_id, id")],
)
class Mute protected constructor(
    @Column(name = "muter_id", nullable = false)
    val muterId: Long,
    @Column(name = "muted_id", nullable = false)
    val mutedId: Long,
) : AggregateRoot() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L
}
