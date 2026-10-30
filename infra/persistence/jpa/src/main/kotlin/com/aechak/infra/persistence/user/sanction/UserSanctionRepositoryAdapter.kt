package com.aechak.infra.persistence.user.sanction

import com.aechak.domain.user.sanction.UserSanction
import com.aechak.domain.user.sanction.repository.UserSanctionRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/** Spring Data 인터페이스는 이 모듈 밖으로 노출되지 않는다 — 어댑터의 내부 부품. */
interface UserSanctionJpaRepository : JpaRepository<UserSanction, Long> {
    // 정렬은 JPQL 소유 — 이력은 추가 전용이라 id 내림차순이 곧 최신순이다
    @Query("select s from UserSanction s where s.targetUser.id = :targetUserId order by s.id desc")
    fun findRecentByTargetUserId(
        @Param("targetUserId") targetUserId: Long,
        pageable: Pageable,
    ): List<UserSanction>

    @Query("select count(s) from UserSanction s where s.targetUser.id = :targetUserId")
    fun countByTargetUserId(
        @Param("targetUserId") targetUserId: Long,
    ): Long
}

@Repository
class UserSanctionRepositoryAdapter(
    private val jpaRepository: UserSanctionJpaRepository,
) : UserSanctionRepository {
    override fun save(sanction: UserSanction): UserSanction = jpaRepository.save(sanction)

    override fun findRecentByTargetUserId(
        targetUserId: Long,
        limit: Int,
    ): List<UserSanction> = jpaRepository.findRecentByTargetUserId(targetUserId, PageRequest.of(0, limit))

    override fun countByTargetUserId(targetUserId: Long): Long = jpaRepository.countByTargetUserId(targetUserId)
}
