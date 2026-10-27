package com.aechak.domain.inspection.repository

import com.aechak.domain.inspection.ContentInspection
import com.aechak.domain.inspection.enums.InspectionTargetType
import java.time.LocalDateTime

interface ContentInspectionRepository {
    fun save(inspection: ContentInspection): ContentInspection

    fun findById(id: Long): ContentInspection?

    /**
     * 판정을 적기 전에 행을 잠근다.
     *
     * 일반 조회로 읽고 쓰면 확인과 쓰기 사이에 다른 판정이나 타임아웃이 끼어든다.
     */
    fun findByIdForUpdate(id: Long): ContentInspection?

    /** 같은 내용을 같은 정책으로 이미 봤는지. 재과금을 막는 조회다 */
    fun findByTarget(
        targetType: InspectionTargetType,
        targetRef: Long,
        contentHash: String,
        policyVersion: String,
    ): ContentInspection?

    /** 대상의 가장 최근 검수. 셀러·구매자 화면이 쓴다 */
    fun findLatestByTarget(
        targetType: InspectionTargetType,
        targetRef: Long,
    ): ContentInspection?

    /** 기한이 지나 판정 없이 닫을 건들 */
    fun findExpired(
        before: LocalDateTime,
        limit: Int,
    ): List<ContentInspection>
}
