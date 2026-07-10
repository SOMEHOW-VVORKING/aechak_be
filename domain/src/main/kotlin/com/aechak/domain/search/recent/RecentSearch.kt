package com.aechak.domain.search.recent

import com.aechak.domain.support.AggregateRoot
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "recent_searches")
class RecentSearch protected constructor(
    userId: Long,
    normalizedKeyword: String,
    displayKeyword: String,
    searchedAt: LocalDateTime,
) : AggregateRoot() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    val userId: Long = userId

    @Column(length = 255, nullable = false)
    val normalizedKeyword: String = normalizedKeyword

    @Column(length = 255, nullable = false)
    val displayKeyword: String = displayKeyword

    @Column(nullable = false)
    val searchedAt: LocalDateTime = searchedAt

    companion object {
        fun record(
            userId: Long,
            normalizedKeyword: String,
            displayKeyword: String,
            searchedAt: LocalDateTime = LocalDateTime.now(),
        ): RecentSearch = RecentSearch(userId, normalizedKeyword, displayKeyword, searchedAt)
    }
}
