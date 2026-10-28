package com.aechak.seller.product.request

import com.aechak.domain.inspection.ProductReviewQueueItem
import jakarta.validation.constraints.Size

/**
 * 검수 이의 제기 요청.
 *
 * 사유 필수(빈 값은 40501)는 정책 소관이라 여긴 길이만 본다. null이어도 형식 오류가 아니라
 * 40501로 나가도록 nullable로 받아 흘려보낸다 — 형식 오류는 셀러에게 무엇을 적어야 하는지
 * 말해 주지 않는다.
 *
 * 길이 상한은 저장 칼럼과 같은 값을 쓴다. 따로 적으면 둘이 갈라지는 순간 500자를 통과한
 * 사유가 저장에서 잘린다.
 */
data class ProductInspectionAppealRequest(
    @field:Size(max = ProductReviewQueueItem.APPEAL_REASON_MAX, message = "이의 제기 사유는 500자 이하여야 합니다.")
    val reason: String?,
)
