package com.aechak.infra.persistence.inspection

import com.aechak.domain.inspection.InspectionFinding
import com.aechak.domain.inspection.enums.InspectionPerspectiveKind
import com.aechak.domain.inspection.repository.InspectionFindingRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface InspectionFindingJpaRepository : JpaRepository<InspectionFinding, Long> {
    fun findAllByInspectionId(inspectionId: Long): List<InspectionFinding>

    fun findByInspectionIdAndPerspective(
        inspectionId: Long,
        perspective: InspectionPerspectiveKind,
    ): InspectionFinding?
}

@Repository
class InspectionFindingRepositoryAdapter(
    private val jpaRepository: InspectionFindingJpaRepository,
) : InspectionFindingRepository {
    /**
     * 관점 단위로 갱신하거나 없으면 만든다.
     *
     * 원자 upsert 쿼리가 아니라 조회 후 갱신인 이유는, 호출부가 이미 검수 행을 잠근 뒤에
     * 들어오기 때문이다. 같은 검수를 두 트랜잭션이 동시에 쓰는 창이 그 잠금으로 닫힌다.
     */
    override fun upsert(finding: InspectionFinding): InspectionFinding {
        val existing = jpaRepository.findByInspectionIdAndPerspective(finding.inspectionId, finding.perspective)
        if (existing == null) return jpaRepository.save(finding)

        existing.record(finding.outcome, finding.skipReason, finding.violations)
        return jpaRepository.save(existing)
    }

    override fun findByInspectionId(inspectionId: Long): List<InspectionFinding> = jpaRepository.findAllByInspectionId(inspectionId)

    override fun findOne(
        inspectionId: Long,
        perspective: InspectionPerspectiveKind,
    ): InspectionFinding? = jpaRepository.findByInspectionIdAndPerspective(inspectionId, perspective)
}
