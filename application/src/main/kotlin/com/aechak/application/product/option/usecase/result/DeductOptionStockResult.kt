package com.aechak.application.product.option.usecase.result

/** 재고 차감 결과. 부족은 정상 사건이라 예외가 아니라 상태로 돌려주고, 거절 코드는 호출 도메인이 고른다 */
enum class DeductOptionStockResult {
    DEDUCTED,
    INSUFFICIENT_STOCK,
}
