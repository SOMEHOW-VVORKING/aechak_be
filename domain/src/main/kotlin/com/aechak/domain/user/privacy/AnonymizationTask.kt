package com.aechak.domain.user.privacy

import com.aechak.domain.support.BaseEntity
import com.aechak.domain.user.privacy.enums.AnonymizationStatus
import com.aechak.domain.user.privacy.enums.AnonymizationTargetType
import com.aechak.domain.user.user.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "anonymization_tasks")
class AnonymizationTask protected constructor(
    user: User,
    targetType: AnonymizationTargetType,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User = user

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    val targetType: AnonymizationTargetType = targetType

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    var status: AnonymizationStatus = AnonymizationStatus.PENDING
        protected set

    @Column
    var processedAt: LocalDateTime? = null
        protected set

    @Column
    var nextRetryAt: LocalDateTime? = null
        protected set

    companion object {
        fun create(
            user: User,
            targetType: AnonymizationTargetType,
        ): AnonymizationTask = AnonymizationTask(user, targetType)
    }
}
