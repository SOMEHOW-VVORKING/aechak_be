package com.aechak.application.inspection.service

import com.aechak.domain.inspection.ProductReviewQueueItem
import java.time.LocalDateTime

/**
 * 읽은 시점의 열린 관리자 검토 행.
 *
 * 엔티티를 그대로 넘기지 않는 이유는 [InspectionSnapshot]과 같다 — 다른 바운디드 컨텍스트가
 * 변경자를 쥐지 않게 한다. 노출 상태를 바꾸는 길은 판정 적용과 관리자 결정 둘뿐이어야 한다.
 *
 * "행이 있다"와 이의 두 칼럼을 한 번에 돌려준다. 따로 읽으면 셀러 조회가 같은 행을 두 번
 * 읽으면서, 그 사이에 행이 닫히면 서로 다른 사실을 섞어 답한다.
 */
data class OpenProductReviewSnapshot(
    /** null이면 이의가 걸리지 않은 행 */
    val appealedAt: LocalDateTime?,
    val appealReason: String?,
) {
    companion object {
        fun from(item: ProductReviewQueueItem): OpenProductReviewSnapshot =
            OpenProductReviewSnapshot(
                appealedAt = item.appealedAt,
                appealReason = item.appealReason,
            )
    }
}
