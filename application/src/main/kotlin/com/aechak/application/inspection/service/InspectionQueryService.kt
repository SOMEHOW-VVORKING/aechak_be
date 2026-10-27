package com.aechak.application.inspection.service

import com.aechak.domain.inspection.enums.InspectionTargetType
import com.aechak.domain.inspection.enums.ReviewQueueStatus
import com.aechak.domain.inspection.repository.ContentInspectionRepository
import com.aechak.domain.inspection.repository.ProductReviewQueueRepository
import org.springframework.stereotype.Service

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
) {
    /** 상품의 가장 최근 검수. 한 번도 검수를 타지 않았으면 null */
    fun findLatestProductInspection(productId: Long): InspectionSnapshot? =
        inspections
            .findLatestByTarget(InspectionTargetType.PRODUCT, productId)
            ?.let(InspectionSnapshot::from)

    fun isAwaitingAdminReview(productId: Long): Boolean = reviewQueue.findPendingByProductId(productId) != null

    /** 가장 최근 관리자 결정이 반려일 때만 그 사유 */
    fun findAdminRejectReason(productId: Long): String? =
        reviewQueue
            .findLatestAdminDecisionByProductId(productId)
            ?.takeIf { it.status == ReviewQueueStatus.REJECTED }
            ?.rejectReason
}
