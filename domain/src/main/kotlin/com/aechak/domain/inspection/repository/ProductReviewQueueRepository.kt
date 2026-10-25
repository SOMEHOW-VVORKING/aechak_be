package com.aechak.domain.inspection.repository

import com.aechak.domain.inspection.ProductReviewQueueItem
import java.time.LocalDateTime

interface ProductReviewQueueRepository {
    fun save(item: ProductReviewQueueItem): ProductReviewQueueItem

    /** 상품의 열린 행. 비잠금 일관 읽기라 읽기 전용 조회만 씀 */
    fun findPendingByProductId(productId: Long): ProductReviewQueueItem?

    /**
     * 상품의 열린 행을 잠금 조회로 읽음. 쓰기 경로는 반드시 이쪽을 써야 함.
     *
     * 상품 행 잠금만으로는 열린 행이 하나로 유지되지 않음. 쓰기 트랜잭션은 잠금을 쥐기 전에
     * 일반 조회를 몇 번 돌고 InnoDB는 그 첫 비잠금 읽기에서 read view를 만든다 — 잠금을 받은
     * 뒤에도 비잠금 조회는 옛 스냅샷을 보므로, 먼저 커밋한 쪽이 넣은 행이 보이지 않아 행이
     * 둘로 갈라지고, 이미 닫힌 행이 열린 것으로 보여 거기에 이의가 적힘. 잠금 조회는 최신
     * 커밋을 읽는 current read라 그 자리가 없어짐.
     *
     * 호출 전에 같은 행을 일반 조회로 먼저 읽지 말 것 — 영속성 컨텍스트에 이미 있으면
     * 잠금 조회가 캐시된 인스턴스를 그대로 돌려줘 같은 구멍이 다시 생긴다.
     */
    fun findPendingByProductIdForUpdate(productId: Long): ProductReviewQueueItem?

    /** 관리자가 반려한 행들. 최근 것부터 */
    fun findRejectedByProductId(productId: Long): List<ProductReviewQueueItem>

    /**
     * 셀러가 지금 열어 둔 이의 건수. 폭주 상한의 분모임.
     *
     * 닫힌 행은 세지 않음 — 셀러가 고친 내용이 통과해 SUPERSEDED로 닫힌 이의는 아무도 결정할
     * 수 없고, 그것을 정원에 세면 "접수 뒤 수정"을 상한만큼 반복한 셀러가 영구히 이의 불가가 됨.
     */
    fun countOpenAppealsByOwner(ownerId: Long): Long

    /** 이 상품의 이의가 마지막으로 기각된 시각. 쿨다운의 기준임. 기각된 적이 없으면 null */
    fun findLastAppealRejectedAt(productId: Long): LocalDateTime?
}
