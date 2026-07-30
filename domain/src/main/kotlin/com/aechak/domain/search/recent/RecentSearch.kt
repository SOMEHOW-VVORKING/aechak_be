package com.aechak.domain.search.recent

import com.aechak.domain.support.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

/**
 * 최근 검색어
 *
 * (user_id, normalized_keyword) 유일 제약으로 같은 키워드는 한 행만 유지
 * 사용자가 지우면 흔적을 남기지 않는 로그성 데이터이므로 하드 삭제
 */
@Entity
@Table(
    name = "recent_searches",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_recent_searches_user_normalized",
            columnNames = ["user_id", "normalized_keyword"],
        ),
    ],
)
class RecentSearch protected constructor(
    userId: Long,
    normalizedKeyword: String,
    displayKeyword: String,
    searchedAt: LocalDateTime,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Column(nullable = false)
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
