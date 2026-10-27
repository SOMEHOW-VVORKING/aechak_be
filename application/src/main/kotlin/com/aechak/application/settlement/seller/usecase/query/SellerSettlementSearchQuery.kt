package com.aechak.application.settlement.seller.usecase.query

import com.aechak.application.settlement.port.SellerSettlementStatus

/** 셀러 정산 목록 조회 입력 — 월·상태 옵셔널 필터 + 오프셋 페이징. 둘 다 null이면 전체 */
data class SellerSettlementSearchQuery(
    val sellerId: Long,
    val settlementMonth: String? = null,
    val status: SellerSettlementStatus? = null,
    val page: Int = 0,
    val size: Int = DEFAULT_SIZE,
) {
    init {
        require(page >= 0) { "page는 0 이상이어야 합니다." }
        require(size in SIZE_MIN..SIZE_MAX) { "size는 $SIZE_MIN~$SIZE_MAX 범위 안에 있어야 합니다." }
        settlementMonth?.let {
            require(MONTH_PATTERN.matches(it)) { "settlementMonth는 $MONTH_FORMAT 형식이어야 합니다." }
        }
    }

    companion object {
        const val DEFAULT_SIZE = 20
        const val MONTH_FORMAT = "yyyy-MM"

        /** 정산월 컬럼에 저장되는 형태와 같은 꼴만 받는다 — 형식이 다르면 어떤 행도 맞지 않아 조용히 빈 결과가 된다 */
        const val MONTH_REGEX = "\\d{4}-(0[1-9]|1[0-2])"

        // @Range(min, max) 속성이 long이라 Long 유지, Int는 컴파일 불가
        const val SIZE_MIN = 1L
        const val SIZE_MAX = 100L

        private val MONTH_PATTERN = Regex("^$MONTH_REGEX$")
    }
}
