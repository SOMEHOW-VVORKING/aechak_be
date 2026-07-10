package com.aechak.domain.user.privacy

import com.aechak.domain.support.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import com.aechak.domain.user.privacy.enums.RetentionRecordType

@Entity
@Table(name = "retention_records")
class RetentionRecord protected constructor(
    recordType: RetentionRecordType,
    userRef: Long,
    retentionExpiryAt: LocalDateTime,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    val recordType: RetentionRecordType = recordType

    /** 연관·FK 금지 — 파기 후 잔존. */
    @Column(name = "user_ref", nullable = false)
    val userRef: Long = userRef

    @Column(nullable = false)
    val retentionExpiryAt: LocalDateTime = retentionExpiryAt

    companion object {
        fun create(
            recordType: RetentionRecordType,
            userRef: Long,
            retentionExpiryAt: LocalDateTime,
        ): RetentionRecord = RetentionRecord(recordType, userRef, retentionExpiryAt)
    }
}
