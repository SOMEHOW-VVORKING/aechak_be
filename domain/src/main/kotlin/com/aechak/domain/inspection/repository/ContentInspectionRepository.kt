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

    /**
     * 판정을 적기 전에 행을 잠그되, 남이 들고 있으면 기다리지 않고 비운다.
     *
     * 기한 정리가 쓴다. 두 스윕이 같은 묶음을 집어 한쪽이 배치 전체를 롤백하는 것을 막는다.
     */
    fun findByIdForUpdateSkipLocked(id: Long): ContentInspection?

    /**
     * 기한이 지나 판정 없이 닫을 건들의 id.
     *
     * 행이 아니라 id이고 잠그지 않는다. 이 조건에 걸 인덱스가 없어 잠금 조회로 훑으면 간극까지
     * 잠겨 그 사이의 상품 등록·수정 전부가 선다. 잠금은 후보마다 기본키로 따로 잡는다.
     *
     * 스냅샷 읽기다. 잠근 뒤에 기한 조건을 다시 확인해야 한다.
     */
    fun findExpiredCandidateIds(
        before: LocalDateTime,
        limit: Int,
    ): List<Long>
}
