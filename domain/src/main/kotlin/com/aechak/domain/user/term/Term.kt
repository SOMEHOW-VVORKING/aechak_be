package com.aechak.domain.user.term

import com.aechak.domain.support.BaseEntity
import com.aechak.domain.user.term.enums.TermType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

@Entity
@Table(
    name = "terms",
    uniqueConstraints = [UniqueConstraint(name = "uk_terms_type_version", columnNames = ["type", "version"])],
)
class Term protected constructor(
    type: TermType,
    isRequired: Boolean,
    version: String,
    title: String,
    body: String,
    effectiveAt: LocalDateTime,
    isActive: Boolean,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    val type: TermType = type

    @Column(nullable = false)
    val isRequired: Boolean = isRequired

    @Column(length = 20, nullable = false)
    val version: String = version

    @Column(length = 255, nullable = false)
    val title: String = title

    @Column(columnDefinition = "TEXT", nullable = false)
    val body: String = body

    @Column(nullable = false)
    val effectiveAt: LocalDateTime = effectiveAt

    @Column(nullable = false)
    var isActive: Boolean = isActive
        protected set

    companion object {
        fun of(
            type: TermType,
            isRequired: Boolean,
            version: String,
            title: String,
            body: String,
            effectiveAt: LocalDateTime,
            isActive: Boolean,
        ): Term = Term(type, isRequired, version, title, body, effectiveAt, isActive)
    }
}
