package com.aechak.domain.product.error

import com.aechak.common.error.ErrorCode

/**
 * product 도메인 에러 코드 — 대역 40000~40999.
 * 새 코드는 이 파일에만 추가한다.
 */
enum class ProductErrorCode(
    override val code: Int,
    override val message: String,
    override val status: Int,
) : ErrorCode {

    PRODUCT_NOT_FOUND(40001, "상품을 찾을 수 없습니다.", 404),
    // TODO: 도메인 기능 구현하며 추가
}
