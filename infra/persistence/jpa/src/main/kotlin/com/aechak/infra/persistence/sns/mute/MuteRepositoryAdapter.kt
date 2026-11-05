package com.aechak.infra.persistence.sns.mute

import com.aechak.domain.sns.mute.Mute
import com.aechak.domain.sns.mute.repository.MuteRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

interface MuteJpaRepository : JpaRepository<Mute, Long> {
    // 멱등 원자 삽입. 중복 키에서 공유 잠금을 잡는 INSERT IGNORE와 달리 배타 잠금을 잡아 끄기와 켜기 둘이 겹쳐도 교착하지 않는다
    @Modifying
    @Query(
        value =
            "INSERT INTO sns_mutes (muter_id, muted_id, created_at, updated_at) " +
                "VALUES (:muterId, :mutedId, NOW(6), NOW(6)) " +
                "ON DUPLICATE KEY UPDATE muter_id = muter_id",
        nativeQuery = true,
    )
    fun insertOrKeep(
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
    ) {
        jpaRepository.insertOrKeep(muterId, mutedId)
    }

    override fun delete(
        muterId: Long,
        mutedId: Long,
    ): Int = jpaRepository.deleteByPair(muterId, mutedId)
}
