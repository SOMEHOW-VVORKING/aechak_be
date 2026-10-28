package com.aechak.infra.persistence.sns.mute

import com.aechak.domain.sns.mute.Mute
import com.aechak.domain.sns.mute.repository.MuteRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

interface MuteJpaRepository : JpaRepository<Mute, Long> {
    // 멱등 원자 삽입 (충돌은 0행으로 흡수), 감사 컬럼은 NOT NULL이라 함께 세팅
    @Modifying
    @Query(
        value =
            "INSERT IGNORE INTO sns_mutes (muter_id, muted_id, created_at, updated_at) " +
                "VALUES (:muterId, :mutedId, NOW(6), NOW(6))",
        nativeQuery = true,
    )
    fun insertIgnore(
        @Param("muterId") muterId: Long,
        @Param("mutedId") mutedId: Long,
    ): Int

    @Modifying
    @Query("delete from Mute m where m.muterId = :muterId and m.mutedId = :mutedId")
    fun deleteByPair(
        @Param("muterId") muterId: Long,
        @Param("mutedId") mutedId: Long,
    ): Int
}

@Repository
class MuteRepositoryAdapter(
    private val jpaRepository: MuteJpaRepository,
) : MuteRepository {
    override fun insertIfAbsent(
        muterId: Long,
        mutedId: Long,
    ): Boolean = jpaRepository.insertIgnore(muterId, mutedId) > 0

    override fun delete(
        muterId: Long,
        mutedId: Long,
    ): Int = jpaRepository.deleteByPair(muterId, mutedId)
}
