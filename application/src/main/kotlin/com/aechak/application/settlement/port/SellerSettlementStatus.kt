package com.aechak.application.settlement.port

/**
 * 셀러 화면이 읽는 정산 상태. 저장된 세 상태(+지급 편성 여부)를 네 가지로 접은 값이다.
 *
 * - [COMPLETED] 지급 완료 / [SCHEDULED] 지급 묶음에 편성됨 / [PENDING] 지급 대기 / [HELD] 진행 중 클레임으로 보류
 * - 취소된 정산 행은 어느 값에도 접히지 않는다 — 집계에서 아예 빠진다.
 *
 * [rank]는 주문 하나로 접을 때의 우선순위다. 정산 행은 주문품목 단위라 한 주문 안에서 상태가 갈릴 수 있어
 * 가장 큰 rank가 주문의 상태가 된다 — 보류가 가장 세고(돈이 막혀 있다), 그다음은 덜 진행된 쪽이다.
 * 값 자체는 SQL의 집계식과 이 enum이 함께 쓰므로 바꾸면 양쪽이 같이 바뀐다.
 */
enum class SellerSettlementStatus(
    val rank: Int,
) {
    COMPLETED(0),
    SCHEDULED(1),
    PENDING(2),
    HELD(3),
    ;

    companion object {
        fun ofRank(rank: Int): SellerSettlementStatus =
            entries.firstOrNull { it.rank == rank }
                ?: throw IllegalStateException("정산 상태 우선순위 $rank 를 해석할 수 없습니다.")
    }
}
