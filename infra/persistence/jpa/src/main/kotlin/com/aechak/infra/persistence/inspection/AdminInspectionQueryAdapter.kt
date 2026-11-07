package com.aechak.infra.persistence.inspection

import com.aechak.application.inspection.port.AdminInspectionPageCondition
import com.aechak.application.inspection.port.AdminInspectionQueryPort
import com.aechak.application.inspection.port.view.AdminInspectionDetailView
import com.aechak.application.inspection.port.view.AdminInspectionRowView
import com.aechak.domain.inspection.QContentInspection
import com.aechak.domain.inspection.enums.InspectionTargetType
import com.aechak.domain.product.product.QProduct
import com.querydsl.core.types.Expression
import com.querydsl.core.types.Predicate
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQuery
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

private val inspection = QContentInspection.contentInspection
private val product = QProduct.product

/**
 * 검수 진행 현황 조회의 QueryDSL 어댑터.
 *
 * 노출 조건을 걸지 않는다. 묻는 것이 "이 검수가 지금 어디까지 왔는가"라서 진행 중인 건이 주인공이고,
 * 판정이 아직 없는 행을 떨어뜨리면 이 화면이 존재할 이유가 사라진다.
 *
 * 상품 이름은 조인으로 함께 가져온다. 행마다 상품을 따로 물으면 조회가 행 수만큼 늘고, 그 비용은
 * 페이지 폭을 늘릴 때마다 커진다.
 *
 * **요청 시각에 인덱스를 두지 않는다.** 이 질의는 상태·종류·판정 등호 + `requested_at` 정렬이다.
 * `content_inspections`의 보조 인덱스는 셋인데 정렬에 닿는 것이 없다 — `(processing_state,
 * next_retry_at)`은 타임아웃 승격이 쓰고, `(target_type, target_ref, id)`는 셀러·구매자 화면이
 * 대상으로 최신 검수를 찾는 경로이고, `(settled_at)`은 지표가 닫힌 건을 세는 자리다. 상태나 종류로
 * 거르면 앞의 둘이 선두 칼럼으로 범위를 좁혀 주지만 둘째 칼럼이 정렬 축과 달라 파일소트는 남고,
 * 필터 없이 열면 전체 스캔이다. 조회만 하는 변경에 스키마를 건드리지 않으려 이번에는 두지 않았다 —
 * 목록이 눈에 보이게 느려지면 `(requested_at)` 또는 `(processing_state, requested_at)`을 더한다.
 * 후자는 기존 `(processing_state, next_retry_at)`과 선두가 겹쳐 승격 조회가 어느 쪽을 집을지
 * 옵티마이저에 달리므로, 더한 뒤 그 경로의 실행 계획을 같이 본다. 더할 때는 마이그레이션과 엔티티
 * 선언을 함께 바꿔야 ddl-auto 환경과 Flyway 환경의 조회 계획이 갈리지 않는다.
 */
@Repository
class AdminInspectionQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : AdminInspectionQueryPort {
    override fun findPage(condition: AdminInspectionPageCondition): List<AdminInspectionRowView> =
        baseQuery(rowProjection())
            .where(*filters(condition))
            // 같은 마이크로초에 요청된 건이 둘이면 순서가 흔들려 오프셋 페이지가 행을 빠뜨리거나 겹친다.
            .orderBy(inspection.requestedAt.desc(), inspection.id.desc())
            .offset(condition.offset)
            .limit(condition.limit.toLong())
            .fetch()

    override fun count(condition: AdminInspectionPageCondition): Long =
        baseQuery(inspection.count()).where(*filters(condition)).fetchOne() ?: 0L

    override fun findDetail(inspectionId: Long): AdminInspectionDetailView? =
        baseQuery(detailProjection()).where(inspection.id.eq(inspectionId)).fetchOne()

    /**
     * 조회와 집계가 같은 조인을 쓰게 한곳에 둔다.
     *
     * 상품 조인은 left join이고 상품 기본키에 걸려 행을 늘리지 않는다 — 집계에 그대로 써도 수가
     * 틀어지지 않는다. inner join으로 두면 상품이 지워진 검수와 리뷰 검수가 목록에서 사라져,
     * 운영자가 "방금 올린 건이 안 보인다"의 원인을 이 화면에서 찾을 수 없게 된다.
     */
    private fun <T> baseQuery(select: Expression<T>): JPAQuery<T> =
        queryFactory
            .select(select)
            .from(inspection)
            .leftJoin(product)
            .on(product.id.eq(inspection.targetRef), inspection.targetType.eq(InspectionTargetType.PRODUCT))

    /**
     * 세 축 모두 선택이고 주지 않으면 전체다.
     *
     * 판정 축에 "아직 없음"은 없다. 그것은 판정 칼럼이 빈 상태이고, 처리 상태(QUEUED·IN_FLIGHT)가
     * 그 축이다. 판정에 가짜 값을 끼워 두 칼럼을 한 축으로 합치면 기한 초과(UNJUDGED)와 아직
     * 안 끝난 건이 같은 칸에 섞인다.
     */
    private fun filters(condition: AdminInspectionPageCondition): Array<Predicate> =
        listOfNotNull(
            condition.processingState?.let(inspection.processingState::eq),
            condition.targetType?.let(inspection.targetType::eq),
            condition.judgment?.let(inspection.judgment::eq),
        ).toTypedArray()

    private fun rowProjection(): Expression<AdminInspectionRowView> =
        Projections.constructor(
            AdminInspectionRowView::class.java,
            inspection.id,
            inspection.targetType,
            inspection.targetRef,
            product.publicId,
            product.name,
            inspection.processingState,
            inspection.judgment,
            inspection.attempt,
            inspection.requestedAt,
            inspection.settledAt,
            inspection.policyVersion,
        )

    private fun detailProjection(): Expression<AdminInspectionDetailView> =
        Projections.constructor(
            AdminInspectionDetailView::class.java,
            inspection.id,
            inspection.targetType,
            inspection.targetRef,
            product.publicId,
            product.name,
            inspection.processingState,
            inspection.judgment,
            inspection.attempt,
            inspection.requestedAt,
            inspection.settledAt,
            inspection.policyVersion,
            inspection.promptVersion,
            inspection.contentHash,
            inspection.reasonCode,
            inspection.reasonSummary,
            inspection.sellerNotice,
            inspection.leaseOwner,
            inspection.leaseExpiresAt,
        )
}
