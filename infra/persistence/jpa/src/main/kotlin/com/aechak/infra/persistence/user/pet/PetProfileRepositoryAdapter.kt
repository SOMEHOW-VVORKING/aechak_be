package com.aechak.infra.persistence.user.pet

import com.aechak.domain.user.pet.PetProfile
import com.aechak.domain.user.pet.enums.PetStatus
import com.aechak.domain.user.pet.repository.PetProfileRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

interface PetProfileJpaRepository : JpaRepository<PetProfile, Long> {
    // breed는 LAZY라 목록에서 라벨을 읽는 순간 마리 수만큼 쿼리가 더 나감
    @Query(
        "select p from PetProfile p join fetch p.breed " +
            "where p.user.id = :userId and p.status = :status " +
            "order by p.isDefault desc, p.createdAt asc, p.id asc",
    )
    fun findActiveByUserIdDefaultFirst(
        @Param("userId") userId: Long,
        @Param("status") status: PetStatus,
    ): List<PetProfile>

    fun findAllByUserIdAndStatusOrderByUpdatedAtDescIdDesc(
        userId: Long,
        status: PetStatus,
    ): List<PetProfile>

    fun countByUserIdAndStatus(
        userId: Long,
        status: PetStatus,
    ): Long
}

@Repository
class PetProfileRepositoryAdapter(
    private val jpaRepository: PetProfileJpaRepository,
) : PetProfileRepository {
    // @Version은 flush 시점에 오름. save만 하면 응답에 옛 version이 실려
    // 클라이언트가 그 값으로 다음 수정을 하면 무조건 409.
    override fun save(pet: PetProfile): PetProfile = jpaRepository.saveAndFlush(pet)

    override fun findAllActiveByUserIdDefaultFirst(userId: Long): List<PetProfile> =
        jpaRepository.findActiveByUserIdDefaultFirst(userId, PetStatus.ACTIVE)

    override fun findAllActiveByUserIdRecentFirst(userId: Long): List<PetProfile> =
        jpaRepository.findAllByUserIdAndStatusOrderByUpdatedAtDescIdDesc(userId, PetStatus.ACTIVE)

    override fun countActiveByUserId(userId: Long): Long = jpaRepository.countByUserIdAndStatus(userId, PetStatus.ACTIVE)
}
