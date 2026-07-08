package com.aechak.domain.order.error

import com.aechak.common.error.ErrorCode

/**
 * order 도메인 에러 코드 — 대역 50000~50999.
 * 새 코드는 이 파일에만 추가한다.
 */
enum class OrderErrorCode(
    override val code: Int,
    override val message: String,
    override val status: Int,
) : ErrorCode {

    ORDER_NOT_FOUND(50001, "주문을 찾을 수 없습니다.", 404),
    // TODO: 도메인 기능 구현하며 추가
}
