package com.aechak.infra.persistence.inspection

import com.aechak.domain.inspection.ContentInspection
import com.aechak.domain.inspection.enums.InspectionTargetType
import com.aechak.domain.inspection.repository.ContentInspectionRepository
import jakarta.persistence.LockModeType
import jakarta.persistence.QueryHint
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.QueryHints
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
     * 판정을 적기 전에 행을 잠그되, 남이 들고 있으면 기다리지 않고 비운다.
     *
     * 기한 정리 스윕이 쓴다. 다중 인스턴스의 두 스윕이 같은 묶음을 집어 한쪽이 배치 전체를
     * 롤백하는 것(큐 등재까지 함께 사라진다)을 막는다. 건너뛴 행은 다음 주기에 집힌다.
     * 잠금 시간 힌트 `-2`가 하이버네이트에서 `SKIP LOCKED`로 번역된다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select i from ContentInspection i where i.id = :id")
    fun findByIdForUpdateSkipLocked(
        @Param("id") id: Long,
    ): ContentInspection?

    /**
     * 기한이 지났고 아직 판정을 못 받은 건들의 id.
     *
     * 명시적으로 판정이 난 건(`judgment is not null`)은 제외한다. 그것까지 풀어 주면
     * 모델이 보류시킨 상품도 기한만 지나면 노출돼 검수가 장식이 된다.
     *
     * 행이 아니라 id를 돌려주고 잠그지 않는 이유는 잠금 범위다. 이 조건에 걸 수 있는 인덱스가
     * 없어 전체 스캔이고, 잠금 조회로 전체를 훑으면 읽은 행마다 넥스트키 잠금을 잡고 마지막에
     * supremum 간극까지 잠근다. 그 간극이 다음 INSERT 자리라서, 스윕 한 번이 그 사이의 상품
     * 등록·수정 전부를 세우고 자기 상품만 만지는 셀러 쓰기와 교착한다. 그래서 후보만 읽고
     * 잠금은 기본키로 행 하나씩 잡는다.
     *
     * 스냅샷 읽기라 잠근 뒤에는 조건을 다시 확인해야 한다. 후보를 고른 뒤 판정이 도착한 행을
     * 그대로 닫으면 "기한만 지나면 통과"가 된다. 그 재확인이 [ContentInspection.isExpired]다.
     */
    @Query(
        """
        select i.id from ContentInspection i
        where i.processingState <> com.aechak.domain.inspection.enums.InspectionProcessingState.SETTLED
          and i.judgment is null
          and i.requestedAt < :before
        order by i.id
        """,
    )
    fun findExpiredCandidateIds(
        @Param("before") before: LocalDateTime,
        limit: Limit,
    ): List<Long>
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

    override fun findByIdForUpdateSkipLocked(id: Long): ContentInspection? = jpaRepository.findByIdForUpdateSkipLocked(id)

    override fun findExpiredCandidateIds(
        before: LocalDateTime,
        limit: Int,
    ): List<Long> = jpaRepository.findExpiredCandidateIds(before, Limit.of(limit))
}
