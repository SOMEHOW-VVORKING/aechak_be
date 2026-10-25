package com.aechak.application.inspection.service

import com.aechak.common.error.BusinessException
import com.aechak.domain.product.error.ProductErrorCode
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 셀러 이의 접수의 폭주 방지 정책.
 *
 * 트랜잭션 애너테이션이 없다. 호출부의 쓰기 트랜잭션 안에서 돌아야 하고, 그 규약은 같은
 * 패키지의 요청 발행·조회 서비스와 같다. 셀러센터가 스캔하는 쪽이 이 패키지다.
 *
 * 잠금 순서와 행 쓰기는 검토 큐 서비스가 한 자리에서 들고, 여기는 정책만 들어 그것을 부른다.
 * 정책 조회(셀러별 건수·상품별 기각 이력)를 상품 행 잠금보다 **먼저** 하는 것은 의도다 —
 * 잠금을 쥔 채 셀러 전체를 세면 그 셀러의 다른 상품 판정까지 함께 기다린다.
 *
 * 그 대가로 상한은 직렬화되지 않는다. 같은 상품을 두 번 누르면 상품 행 잠금과 이의 중복
 * 검사가 막지만, 서로 다른 상품 두 건을 동시에 누르면 상한을 한 칸 넘길 수 있다. 막으려면
 * 검수 경로가 셀러 행을 잠가야 하고 그것은 교착 면을 넓힌다 — 상한 3에 초과 폭 1이라 둔다.
 */
@Service
class InspectionAppealService(
    private val reviewQueueService: ProductReviewQueueService,
    /** 셀러 한 명이 동시에 열어 둘 수 있는 이의 건수 */
    @Value("\${inspection.appeal.max-open-per-seller:3}") private val maxOpenPerSeller: Int,
    /** 이의가 기각된 뒤 같은 상품에 다시 제기할 수 없는 시간 */
    @Value("\${inspection.appeal.cooldown-hours:24}") private val cooldownHours: Long,
) {
    /**
     * 사유는 필수다. 빈 값을 형식 오류로 돌려주지 않고 전용 코드로 답하기 위해 nullable로 받는다.
     *
     * 접수는 노출을 바꾸지 않는다. 자격이 열리는 신호는 상품의 노출 상태가 아니라
     * 열린 검토 행이고, 그 판단은 검토 큐 서비스가 상품 행을 잠근 뒤에 한다.
     */
    fun submit(
        sellerId: Long,
        productId: Long,
        reason: String?,
        now: LocalDateTime = LocalDateTime.now(),
    ) {
        val trimmed = reason?.trim()
        if (trimmed.isNullOrEmpty()) throw BusinessException(ProductErrorCode.INSPECTION_APPEAL_REASON_REQUIRED)

        requireWithinOpenLimit(sellerId)
        requireCooldownPassed(productId, now)
        reviewQueueService.appealBySeller(productId, trimmed, now)
    }

    private fun requireWithinOpenLimit(sellerId: Long) {
        if (reviewQueueService.countOpenAppeals(sellerId) >= maxOpenPerSeller) {
            throw BusinessException(ProductErrorCode.INSPECTION_APPEAL_LIMIT_EXCEEDED)
        }
    }

    /**
     * "수정 -> 이의 -> 수정 -> 이의" 루프를 끊는다.
     *
     * 기각 이력을 상품 단위로 본다. 행 단위로 보면 내용이 바뀔 때마다 큐 행이 갈려 쿨다운이
     * 걸리지 않는다.
     */
    private fun requireCooldownPassed(
        productId: Long,
        now: LocalDateTime,
    ) {
        val lastRejectedAt = reviewQueueService.findLastAppealRejectedAt(productId) ?: return
        if (lastRejectedAt.plusHours(cooldownHours).isAfter(now)) {
            throw BusinessException(ProductErrorCode.INSPECTION_APPEAL_COOLDOWN)
        }
    }
}
