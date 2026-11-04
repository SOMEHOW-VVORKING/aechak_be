package com.aechak.infra.persistence.sns.hashtag

import com.aechak.domain.sns.hashtag.Hashtag
import com.aechak.domain.sns.hashtag.repository.HashtagRepository
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

interface HashtagJpaRepository : JpaRepository<Hashtag, Long> {
    fun findByName(name: String): Hashtag?

    fun findAllByNameIn(names: Collection<String>): List<Hashtag>

    @Modifying
    @Query(
        value =
            "INSERT INTO sns_hashtags (name, post_count, created_at, updated_at) " +
                "VALUES (:name, 1, NOW(6), NOW(6)) " +
                "ON DUPLICATE KEY UPDATE post_count = post_count + 1, updated_at = NOW(6)",
        nativeQuery = true,
    )
    fun increasePostCount(
        @Param("name") name: String,
    ): Int

    @Modifying
    @Query(
        "update Hashtag h set h.postCount = h.postCount - 1, h.updatedAt = CURRENT_TIMESTAMP " +
            "where h.name = :name and h.postCount > 0",
    )
    fun decreasePostCount(
        @Param("name") name: String,
    ): Int

    // 이스케이프 문자를 쿼리에 적어 DB 기본값에 기대지 않는다
    @Query(
        "select h from Hashtag h where h.postCount > 0 and h.name like :pattern escape '\\' " +
            "order by h.postCount desc, h.name asc",
    )
    fun findPopularByNameLike(
        @Param("pattern") pattern: String,
        limit: Limit,
    ): List<Hashtag>
}

/** 검색어를 글자 그대로 부분 일치하는 LIKE 패턴으로 바꾼다. 이스케이프 문자를 먼저 이스케이프한다 */
private fun nameContainsPattern(keyword: String): String = "%" + keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"

// shortcut: 자동완성은 앞에 와일드카드가 붙은 LIKE라 sns_hashtags를 끝까지 읽는다.
// 태그가 10만 개를 넘거나 자동완성 응답 P95가 100ms를 넘으면 ngram FULLTEXT 인덱스로 바꾼다.
// shortcut: 인기 태그 행 하나는 그 태그를 단 모든 게시와 삭제가 차례로 잠근다.
// 같은 태그 행의 잠금 대기가 게시 응답 시간에 드러나면 수 갱신을 비동기 재계산으로 옮긴다.
@Repository
class HashtagRepositoryAdapter(
    private val jpaRepository: HashtagJpaRepository,
) : HashtagRepository {
    // 늘릴 태그와 줄일 태그를 이름 오름차순 한 번으로 잠가 게시, 수정, 삭제가 어떻게 겹쳐도 같은 순서로 기다린다
    override fun adjustCounts(
        added: Collection<String>,
        removed: Collection<String>,
    ) {
        val increased = added.toSet() - removed.toSet()
        val decreased = removed.toSet() - added.toSet()
        (increased + decreased).sorted().forEach { name ->
            if (name in increased) jpaRepository.increasePostCount(name) else jpaRepository.decreasePostCount(name)
        }
    }

    override fun findByName(name: String): Hashtag? = jpaRepository.findByName(name)

    override fun findAllByNameIn(names: Collection<String>): List<Hashtag> =
        if (names.isEmpty()) emptyList() else jpaRepository.findAllByNameIn(names)

    override fun findAllById(ids: Collection<Long>): List<Hashtag> = jpaRepository.findAllById(ids)

    override fun findPopular(
        keyword: String,
        limit: Int,
    ): List<Hashtag> = jpaRepository.findPopularByNameLike(nameContainsPattern(keyword), Limit.of(limit))
}
