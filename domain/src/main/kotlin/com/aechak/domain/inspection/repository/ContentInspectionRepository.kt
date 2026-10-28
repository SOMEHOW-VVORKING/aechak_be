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
     * 대상의 가장 최근 **판정이 적힌** 검수. 열린 검토 행을 지금 막고 있는 판정이다.
     *
     * 이의의 지문이 여기서 나온다. 판정 여부를 보지 않고 최신 검수를 쓰면, 셀러가 내용을 고쳐
     * 재검수가 아직 미판정으로 걸려 있는 사이에 접수한 이의가 그 **고친 내용**의 지문을 달고,
     * 몇 분 뒤 그 내용의 판정이 와도 지문이 같아 무효가 되지 않는다.
     */
    fun findLatestSettledByTarget(
        targetType: InspectionTargetType,
        targetRef: Long,
    ): ContentInspection?

    /**
     * 대상의 검수 가운데 **판정 시각이 이 값인 것**. 관리자 검토 행을 연 판정을 되찾는 조회다.
     *
     * 검토 행의 `blinded_at`은 그 행을 열었거나 다시 연 판정의 `settled_at`을 그대로 복사한
     * 값이다. 그래서 이 조회가 "지금 그 행을 막고 있는 판정"을 돌려준다. 화면이 가장 최근
     * 검수에서 사유를 읽으면, 모델이 답을 못 준 판정(판정 실패·기한 만료·증거 없는 통과)이
     * 그 위를 덮어 사유가 빈다 — 그것들은 상품도 큐도 건드리지 않으므로 막고 있는 것은
     * 여전히 앞의 판정인데 화면만 비는 것이다.
     *
     * 시각은 유일 키가 아니다. 같은 마이크로초에 판정된 검수가 둘이면 id가 큰 쪽을 고른다.
     */
    fun findLatestByTargetAndSettledAt(
        targetType: InspectionTargetType,
        targetRef: Long,
        settledAt: LocalDateTime,
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
