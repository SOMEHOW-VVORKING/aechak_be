package com.aechak.infra.persistence.inspection

import com.aechak.domain.inspection.ContentInspection
import com.aechak.domain.inspection.enums.InspectionTargetType
import com.aechak.domain.inspection.repository.ContentInspectionRepository
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

interface ContentInspectionJpaRepository : JpaRepository<ContentInspection, Long> {
    /**
     * 판정을 적기 전에 행을 잠근다.
     *
     * 확인과 쓰기 사이에 다른 판정이나 타임아웃 승격이 끼어드는 창을 없애는 것이 목적이다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ContentInspection i where i.id = :id")
    fun findByIdForUpdate(
        @Param("id") id: Long,
    ): ContentInspection?

    fun findByTargetTypeAndTargetRefAndContentHashAndPolicyVersion(
        targetType: InspectionTargetType,
        targetRef: Long,
        contentHash: String,
        policyVersion: String,
    ): ContentInspection?

    fun findFirstByTargetTypeAndTargetRefOrderByIdDesc(
        targetType: InspectionTargetType,
        targetRef: Long,
    ): ContentInspection?

    /**
     * 기한이 지났고 아직 판정을 못 받은 건들.
     *
     * 명시적으로 판정이 난 건(`judgment is not null`)은 제외한다. 그것까지 풀어 주면
     * 모델이 보류시킨 상품도 기한만 지나면 노출돼 검수가 장식이 된다.
     */
    @Query(
        """
        select i from ContentInspection i
        where i.processingState <> com.aechak.domain.inspection.enums.InspectionProcessingState.SETTLED
          and i.judgment is null
          and i.requestedAt < :before
        order by i.id
        """,
    )
    fun findExpired(
        @Param("before") before: LocalDateTime,
        limit: Limit,
    ): List<ContentInspection>
}

@Repository
class ContentInspectionRepositoryAdapter(
    private val jpaRepository: ContentInspectionJpaRepository,
) : ContentInspectionRepository {
    override fun save(inspection: ContentInspection): ContentInspection = jpaRepository.save(inspection)

    override fun findById(id: Long): ContentInspection? = jpaRepository.findById(id).orElse(null)

    override fun findByIdForUpdate(id: Long): ContentInspection? = jpaRepository.findByIdForUpdate(id)

    override fun findByTarget(
        targetType: InspectionTargetType,
        targetRef: Long,
        contentHash: String,
        policyVersion: String,
    ): ContentInspection? =
        jpaRepository.findByTargetTypeAndTargetRefAndContentHashAndPolicyVersion(
            targetType,
            targetRef,
            contentHash,
            policyVersion,
        )

    override fun findLatestByTarget(
        targetType: InspectionTargetType,
        targetRef: Long,
    ): ContentInspection? = jpaRepository.findFirstByTargetTypeAndTargetRefOrderByIdDesc(targetType, targetRef)

    override fun findExpired(
        before: LocalDateTime,
        limit: Int,
    ): List<ContentInspection> = jpaRepository.findExpired(before, Limit.of(limit))
}
