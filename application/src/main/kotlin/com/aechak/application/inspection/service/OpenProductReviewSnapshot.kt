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
    /**
     * 이 행을 열었거나 다시 연 판정의 시각.
     *
     * 그 판정을 되찾는 열쇠다. 화면의 사유와 진행 단계가 "가장 최근 검수"가 아니라 "지금
     * 이 행을 막고 있는 판정"에서 나와야 하고, 둘은 모델이 답을 못 준 뒤에 갈라진다.
     */
    val blindedAt: LocalDateTime,
    /** null이면 이의가 걸리지 않은 행 */
    val appealedAt: LocalDateTime?,
    val appealReason: String?,
    /**
     * 이의를 접수했는데 내용이 바뀌어 무효가 된 행인가.
     *
     * [appealedAt]만 내려보내면 "접수한 적 없음"과 "접수했지만 무효가 됨"이 셀러에게 같은
     * 모양으로 나가 접수가 실패한 것과 구별되지 않는다.
     */
    val appealCleared: Boolean,
) {
    companion object {
        fun from(item: ProductReviewQueueItem): OpenProductReviewSnapshot =
            OpenProductReviewSnapshot(
                blindedAt = item.blindedAt,
                appealedAt = item.appealedAt,
                appealReason = item.appealReason,
                appealCleared = item.appealCleared(),
            )
    }
}
