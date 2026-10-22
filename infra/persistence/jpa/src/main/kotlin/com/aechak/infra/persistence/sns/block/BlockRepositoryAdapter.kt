package com.aechak.infra.persistence.sns.block

import com.aechak.domain.sns.block.Block
import com.aechak.domain.sns.block.repository.BlockRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

interface BlockJpaRepository : JpaRepository<Block, Long> {
    // 멱등 원자 삽입 (충돌은 0행으로 흡수), 감사 컬럼은 NOT NULL이라 함께 세팅
    @Modifying
    @Query(
        value =
            "INSERT IGNORE INTO sns_blocks (blocker_id, blocked_id, created_at, updated_at) " +
                "VALUES (:blockerId, :blockedId, NOW(6), NOW(6))",
        nativeQuery = true,
    )
    fun insertIgnore(
        @Param("blockerId") blockerId: Long,
        @Param("blockedId") blockedId: Long,
    ): Int

    // 두 방향 쌍을 쌍 UNIQUE 인덱스로 찾아 있는 행에 공유 잠금을 잡는다
    @Query(
        value =
            "SELECT blocker_id FROM sns_blocks " +
                "WHERE (blocker_id, blocked_id) IN ((:viewerId, :targetId), (:targetId, :viewerId)) FOR SHARE",
        nativeQuery = true,
    )
    fun findBlockerIdsForShare(
        @Param("viewerId") viewerId: Long,
        @Param("targetId") targetId: Long,
    ): List<Long>

    @Modifying
    @Query("delete from Block b where b.blockerId = :blockerId and b.blockedId = :blockedId")
    fun deleteByPair(
        @Param("blockerId") blockerId: Long,
        @Param("blockedId") blockedId: Long,
    ): Int
}

@Repository
class BlockRepositoryAdapter(
    private val jpaRepository: BlockJpaRepository,
) : BlockRepository {
    override fun insertIfAbsent(
        blockerId: Long,
        blockedId: Long,
    ): Boolean = jpaRepository.insertIgnore(blockerId, blockedId) > 0

    override fun delete(
        blockerId: Long,
        blockedId: Long,
    ): Int = jpaRepository.deleteByPair(blockerId, blockedId)

    override fun findDirectionsForShare(
        viewerId: Long,
        targetId: Long,
    ): Set<Long> = jpaRepository.findBlockerIdsForShare(viewerId, targetId).toSet()
}
