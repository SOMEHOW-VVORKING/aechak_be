package com.aechak.domain.inspection

import com.aechak.domain.inspection.enums.InspectionPerspectiveKind
import com.aechak.domain.inspection.enums.PerspectiveOutcome
import com.aechak.domain.support.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 한 관점이 무엇을 봤는지의 기록.
 *
 * 검수 한 건에 관점마다 한 행이고, 같은 메시지가 다시 와도 누적이 아니라 갱신한다.
 * 누적하면 재전달마다 같은 위반이 쌓여 집계가 부풀려진다.
 *
 * 통과와 미검사를 `outcome`으로 구분하는 것이 이 테이블의 존재 이유다. 뭉개면 모델이
 * 보지 못한 것이 "문제 없음"으로 남는다.
 */
@Entity
@Table(
    name = "inspection_findings",
    // ddl-auto로 스키마를 만드는 테스트에서도 누적이 막히도록 엔티티에 함께 선언한다.
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_inspection_findings_perspective",
            columnNames = ["inspection_id", "perspective"],
        ),
    ],
)
class InspectionFinding protected constructor(
    @Column(nullable = false, updatable = false)
    val inspectionId: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    val perspective: InspectionPerspectiveKind,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var outcome: PerspectiveOutcome = PerspectiveOutcome.UNCHECKED
        protected set

    @Column(length = 60)
    var skipReason: String? = null
        protected set

    /** 위반 상세 JSON. 도메인은 분류의 의미를 따지지 않고 저장과 노출만 책임진다 */
    @Column(columnDefinition = "JSON")
    var violations: String? = null
        protected set

    fun record(
        outcome: PerspectiveOutcome,
        skipReason: String?,
        violations: String?,
    ) {
        this.outcome = outcome
        this.skipReason = skipReason
        this.violations = violations
    }

    companion object {
        fun of(
            inspectionId: Long,
            perspective: InspectionPerspectiveKind,
            outcome: PerspectiveOutcome,
            skipReason: String? = null,
            violations: String? = null,
        ): InspectionFinding =
            InspectionFinding(inspectionId, perspective).apply {
                record(outcome, skipReason, violations)
            }
    }
}
