package com.aechak.application.product.option.usecase.result

/** 재고 복원 결과. 실패 사유를 담는 이유는 호출측이 수동 복원 안내를 남겨야 하기 때문이다 */
data class RestoreOptionStockResult(
    val status: RestoreOptionStockStatus,
    val currentStock: Int?,
) {
    companion object {
        fun restored(): RestoreOptionStockResult = RestoreOptionStockResult(RestoreOptionStockStatus.RESTORED, null)

        fun of(
            status: RestoreOptionStockStatus,
            currentStock: Int,
        ): RestoreOptionStockResult = RestoreOptionStockResult(status, currentStock)
    }
}

enum class RestoreOptionStockStatus {
    RESTORED,

    /** 복원하면 재고 컬럼이 담을 수 있는 값을 넘긴다 */
    STOCK_LIMIT_EXCEEDED,

    /** 복원 대상 옵션조합이 없다 */
    OPTION_NOT_FOUND,

    /** 조건부 UPDATE가 0행인데 상한에도 걸리지 않았다 — 데이터 상태를 사람이 봐야 한다 */
    UNKNOWN,
}
