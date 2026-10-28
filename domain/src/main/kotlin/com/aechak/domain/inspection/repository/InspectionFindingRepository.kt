package com.aechak.domain.inspection.repository

import com.aechak.domain.inspection.InspectionFinding
import com.aechak.domain.inspection.enums.InspectionPerspectiveKind

interface InspectionFindingRepository {
    /** 관점 단위로 갱신하거나 없으면 만든다. 재전달에 행이 쌓이지 않게 하는 지점이다 */
    fun upsert(finding: InspectionFinding): InspectionFinding

    fun findByInspectionId(inspectionId: Long): List<InspectionFinding>

    fun findOne(
        inspectionId: Long,
        perspective: InspectionPerspectiveKind,
    ): InspectionFinding?
}
