package com.aechak.application.order.seller.usecase.query

import com.aechak.application.order.seller.port.SellerOrderStatusFilter
import java.time.LocalDate

/** 셀러 주문 목록 조회 입력 — 상태 세그먼트 + 키워드·주문일 범위 + 오프셋 페이징. */
data class SellerOrderSearchQuery(
    val sellerId: Long,
    val filter: SellerOrderStatusFilter = SellerOrderStatusFilter.ALL,
    val keyword: String? = null,
    val orderedFrom: LocalDate? = null,
    val orderedTo: LocalDate? = null,
    val page: Int = 0,
    val size: Int = DEFAULT_SIZE,
) {
    init {
        require(page >= 0) { "page는 0 이상이어야 합니다." }
        require(size in SIZE_MIN..SIZE_MAX) { "size는 $SIZE_MIN~$SIZE_MAX 범위 안에 있어야 합니다." }
        keyword?.let {
            require(it.isNotBlank()) { "keyword는 공백일 수 없습니다." }
            require(it.length <= KEYWORD_MAX) { "keyword는 최대 $KEYWORD_MAX 자입니다." }
        }
        if (orderedFrom != null && orderedTo != null) {
            require(!orderedFrom.isAfter(orderedTo)) { "주문일 필터의 시작이 끝보다 늦을 수 없습니다." }
        }
    }

    companion object {
        const val DEFAULT_SIZE = 20
        const val KEYWORD_MAX = 100

        // @Range(min, max) 속성이 long이라 Long 유지, Int는 컴파일 불가
        const val SIZE_MIN = 1L
        const val SIZE_MAX = 100L
    }
}
