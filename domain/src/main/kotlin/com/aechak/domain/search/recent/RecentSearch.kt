package com.aechak.domain.search.recent

import com.aechak.domain.support.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.text.Normalizer
import java.time.LocalDateTime

/**
 * 최근 검색어
 *
 * (user_id, keyword) 유일 제약으로 같은 키워드는 한 행만 유지
 * keyword는 대소문자 구분(as_cs 콜레이션)
 * 사용자가 지우면 흔적을 남기지 않는 로그성 데이터이므로 하드 삭제
 */
@Entity
@Table(
    name = "recent_searches",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_recent_searches_user_keyword",
            columnNames = ["user_id", "keyword"],
        ),
    ],
)
class RecentSearch protected constructor(
    userId: Long,
    keyword: String,
    searchedAt: LocalDateTime,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Column(nullable = false)
    val userId: Long = userId

    // 대소문자와 악센트 구분 UNIQUE하게 처리하여 사용자가 친 표기를 각각 보존
    @Column(columnDefinition = "varchar(255) collate utf8mb4_0900_as_cs not null")
    val keyword: String = keyword

    @Column(nullable = false)
    val searchedAt: LocalDateTime = searchedAt

    companion object {
        private val WHITESPACE_RUN = Regex("[\\s\\p{Z}\\x{0085}]+")

        /** 저장용 키워드 정규화(NFC + 내부 공백 접기 + trim) */
        fun normalizeKeyword(raw: String): String =
            Normalizer
                .normalize(raw, Normalizer.Form.NFC)
                .replace(WHITESPACE_RUN, " ")
                .trim()

        fun record(
            userId: Long,
            keyword: String,
            searchedAt: LocalDateTime = LocalDateTime.now(),
        ): RecentSearch = RecentSearch(userId, keyword, searchedAt)
    }
}
