package com.aechak.domain.search.keyword

import com.aechak.domain.support.AggregateRoot
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "recommended_keywords")
class RecommendedKeyword protected constructor(
    keyword: String,
    sortOrder: Int,
    isActive: Boolean,
) : AggregateRoot() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Column(length = 100, nullable = false)
    var keyword: String = keyword
        protected set

    var sortOrder: Int = sortOrder
        protected set

    var isActive: Boolean = isActive
        protected set

    companion object {
        fun register(
            keyword: String,
            sortOrder: Int,
            isActive: Boolean = true,
        ): RecommendedKeyword = RecommendedKeyword(keyword, sortOrder, isActive)
    }
}
