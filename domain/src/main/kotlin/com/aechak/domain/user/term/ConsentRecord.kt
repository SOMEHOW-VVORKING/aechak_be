package com.aechak.domain.user.term

import com.aechak.domain.support.BaseEntity
import com.aechak.domain.user.user.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(
    name = "consent_records",
    // 유효 동의 = (user, term) 최신 행(MAX(id)) 판정용 커버링 인덱스
    indexes = [Index(name = "ix_consent_records_user_term_id", columnList = "user_id, term_id, id")],
)
class ConsentRecord protected constructor(
    user: User,
    term: Term,
    isAgreed: Boolean,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User = user

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "term_id", nullable = false)
    val term: Term = term

    @Column(nullable = false)
    val isAgreed: Boolean = isAgreed

    companion object {
        fun record(
            user: User,
            term: Term,
            isAgreed: Boolean,
        ): ConsentRecord = ConsentRecord(user, term, isAgreed)
    }
}
