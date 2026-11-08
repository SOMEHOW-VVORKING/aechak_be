package com.aechak.application.inspection.service

import com.aechak.application.inspection.port.ViolationDeserializer
import com.aechak.application.inspection.port.view.ViolationView
import com.aechak.application.inspection.usecase.result.InspectionStageResult
import com.aechak.domain.inspection.InspectionFinding
import com.aechak.domain.inspection.enums.InspectionTargetType
import com.aechak.domain.inspection.enums.ReviewQueueStatus
import com.aechak.domain.inspection.repository.ContentInspectionRepository
import com.aechak.domain.inspection.repository.InspectionFindingRepository
import com.aechak.domain.inspection.repository.ProductReviewQueueRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 검수 기록을 읽는다.
 *
 * 트랜잭션 애너테이션이 없다. 호출부의 읽기 트랜잭션 안에서 돌아야 하고, 그 규약은 같은
 * 패키지의 요청 발행 서비스와 같다. 판정을 적용하는 파사드는 api가 소유하는데 셀러센터도
 * 이 읽기가 필요해서, 셀러가 스캔하는 쪽에 둔다.
 */
@Service
class InspectionQueryService(
    private val inspections: ContentInspectionRepository,
    private val reviewQueue: ProductReviewQueueRepository,
    private val findings: InspectionFindingRepository,
    private val violationDeserializer: ViolationDeserializer,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 상품의 가장 최근 검수. 한 번도 검수를 타지 않았으면 null */
    fun findLatestProductInspection(productId: Long): InspectionSnapshot? =
        inspections
            .findLatestByTarget(InspectionTargetType.PRODUCT, productId)
            ?.let(InspectionSnapshot::from)

    /**
     * 상품의 가장 최근 **판정이 적힌** 검수.
     *
     * 이의가 검토 행을 새로 열 때(관리자 반려 뒤) 그 행의 시각을 여기서 읽는다. 그 칼럼의 뜻이
     * "행을 열었거나 다시 연 판정의 시각"이라서 미판정 검수의 것을 쓸 수 없고, 접수 시각으로
     * 채우면 관리자 목록의 정렬 기준이 경로마다 갈린다.
     *
     * **이것이 "지금 그 행을 막고 있는 판정"은 아니다.** 위반 뒤에 판정 실패가 하나 오면
     * 가장 최근 판정된 검수는 그 실패이지만 막고 있는 것은 여전히 그 위반이다. 그쪽은
     * [findBlockingProductInspection]이다.
     */
    fun findLatestSettledProductInspection(productId: Long): InspectionSnapshot? =
        inspections
            .findLatestSettledByTarget(InspectionTargetType.PRODUCT, productId)
            ?.let(InspectionSnapshot::from)

    /**
     * 상품의 열린 관리자 검토 행. 없으면 null — 사람 확인을 기다리지 않는다는 뜻이다.
     *
     * 행의 존재와 이의 두 칼럼을 한 번에 돌려준다. 셀러 조회가 둘을 따로 읽으면 같은 행을 두 번
     * 읽으면서 그 사이에 행이 닫힐 수 있다.
     */
    fun findOpenProductReview(productId: Long): OpenProductReviewSnapshot? =
        reviewQueue.findPendingByProductId(productId)?.let(OpenProductReviewSnapshot::from)

    /**
     * 열린 검토 행을 **지금 막고 있는 판정**. 행의 시각에 대응하는 검수가 없으면 null.
     *
     * [blindedAt]은 그 행을 열었거나 다시 연 판정의 시각이고, 판정 반영이 검수의 판정 시각을
     * 그대로 복사해 넣은 값이다. 셀러·관리자 화면의 사유를 이것에서 읽어야 한다 — 가장 최근
     * 검수에서 읽으면 모델이 답을 못 준 판정이 그 위를 덮어 "반려는 남고 사유만 없어진" 화면이
     * 된다. 셀러는 무엇을 고칠지 모르고 관리자는 무엇을 풀지 모른다.
     *
     * 이의가 연 행은 막는 판정이 아예 없을 때 접수 시각을 쓰므로 null이 온다. 그때는 호출부가
     * 가장 최근 검수로 떨어진다 — 지금까지의 동작과 같다.
     */
    fun findBlockingProductInspection(
        productId: Long,
        blindedAt: LocalDateTime,
    ): InspectionSnapshot? =
        inspections
            .findLatestByTargetAndSettledAt(InspectionTargetType.PRODUCT, productId, blindedAt)
            ?.let(InspectionSnapshot::from)

    /**
     * 검수 한 건의 관점 행을 **한 번 읽어** 위반 목록과 단계 셋을 함께 돌려준다.
     *
     * 두 투영을 따로 열지 않는 이유가 질의 수다. 셀러 검수 상세는 둘을 늘 같이 쓰므로, 메서드를
     * 나눠 두면 같은 SELECT 가 요청마다 두 번 돈다.
     *
     * 투영 자체는 섞지 않는다 -- 위반 쪽은 관점 차원을 버려 목록을 합치는 것이 답이고 단계 쪽은
     * "어느 단계까지 봤는가"가 답이라, 같이 뭉개면 미검사가 통과로 보인다.
     */
    fun findDetail(snapshot: InspectionSnapshot): InspectionDetailView {
        val rows = findings.findByInspectionId(snapshot.inspectionId)
        return InspectionDetailView(
            violations = foldViolations(rows),
            stages = InspectionStageResult.fold(rows, snapshot.processingState),
        )
    }

    /**
     * 검수 한 건의 위반 목록만. 단계를 쓰지 않는 어드민 상세가 이것을 부른다.
     *
     * 셀러 상세는 [findDetail]을 쓴다 -- 둘을 다 쓰는 자리에서 이것을 부르면 질의가 두 번 돈다.
     */
    fun findViolations(inspectionId: Long): List<ViolationView> = foldViolations(findings.findByInspectionId(inspectionId))

    /** 관점별 위반을 합쳐 글 안의 위치 순서로 돌려줌. 읽지 못한 행은 건너뜀 */
    private fun foldViolations(rows: List<InspectionFinding>): List<ViolationView> =
        rows
            .sortedBy { it.id }
            .flatMap(::readViolations)
            .groupBy { it.category to it.quote }
            .values
            .map { sameViolations ->
                sameViolations.first().copy(
                    spanStart = sameViolations.mapNotNull { it.spanStart }.minOrNull(),
                    suggestion = sameViolations.firstNotNullOfOrNull { it.suggestion?.takeIf(String::isNotBlank) },
                )
            }.sortedWith(
                compareBy<ViolationView, Int?>(nullsLast()) { it.spanStart }
                    .thenBy { it.category }
                    .thenBy(nullsLast()) { it.quote },
            )

    private fun readViolations(finding: InspectionFinding): List<ViolationView> {
        val stored = finding.violations ?: return emptyList()
        val violations = violationDeserializer.fromJson(stored)
        if (violations == null) {
            log.warn("저장된 위반을 읽지 못해 그 행을 건너뛴다. inspectionId={}", finding.inspectionId)
            return emptyList()
        }
        return violations
    }

    /** 가장 최근 관리자 결정이 반려일 때만 그 사유 */
    fun findAdminRejectReason(productId: Long): String? =
        reviewQueue
            .findLatestAdminDecisionByProductId(productId)
            ?.takeIf { it.status == ReviewQueueStatus.REJECTED }
            ?.rejectReason
}
