package com.aechak.domain.review.review

import com.aechak.domain.support.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "review_images")
class ReviewImage protected constructor(
    @Column(length = 512, nullable = false)
    val storageKey: String,
    sortOrder: Int,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    var sortOrder: Int = sortOrder
        protected set

    var deletedAt: LocalDateTime? = null
        protected set

    companion object {
        fun of(storageKey: String, sortOrder: Int): ReviewImage = ReviewImage(storageKey, sortOrder)
    }
}
