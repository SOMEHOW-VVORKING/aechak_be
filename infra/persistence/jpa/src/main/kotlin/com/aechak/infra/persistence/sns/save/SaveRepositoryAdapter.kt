package com.aechak.infra.persistence.sns.save

import com.aechak.domain.sns.save.Save
import com.aechak.domain.sns.save.repository.SaveRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

interface SaveJpaRepository : JpaRepository<Save, Long> {
    // 멱등 원자 삽입. 중복 키에서 배타 잠금을 잡아 INSERT IGNORE보다 교착이 드물다. 끄기 하나와 켜기 둘이 겹쳐 드물게 나는 교착은 Facade가 한 번 다시 실행한다
    @Modifying
    @Query(
        value =
            "INSERT INTO sns_saves (user_id, post_id, created_at, updated_at) " +
                "VALUES (:userId, :postId, NOW(6), NOW(6)) " +
                "ON DUPLICATE KEY UPDATE user_id = user_id",
        nativeQuery = true,
    )
    fun insertOrKeep(
        @Param("userId") userId: Long,
        @Param("postId") postId: Long,
    ): Int

    @Modifying
    @Query("delete from Save s where s.userId = :userId and s.postId = :postId")
    fun deleteByPair(
        @Param("userId") userId: Long,
        @Param("postId") postId: Long,
    ): Int

    @Query("select s.postId from Save s where s.userId = :userId and s.postId in :postIds")
    fun findPostIds(
        @Param("userId") userId: Long,
        @Param("postIds") postIds: Collection<Long>,
    ): List<Long>
}

@Repository
class SaveRepositoryAdapter(
    private val jpaRepository: SaveJpaRepository,
) : SaveRepository {
    override fun insertIfAbsent(
        userId: Long,
        postId: Long,
    ) {
        jpaRepository.insertOrKeep(userId, postId)
    }

    override fun delete(
        userId: Long,
        postId: Long,
    ): Int = jpaRepository.deleteByPair(userId, postId)

    override fun findSavedPostIdsAmong(
        userId: Long,
        postIds: Collection<Long>,
    ): Set<Long> = jpaRepository.findPostIds(userId, postIds).toSet()
}
