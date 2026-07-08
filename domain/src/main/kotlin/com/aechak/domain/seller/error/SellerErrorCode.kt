package com.aechak.domain.seller.error

import com.aechak.common.error.ErrorCode

/**
 * seller 도메인 에러 코드 — 대역 10000~10999.
 * 새 코드는 이 파일에만 추가한다.
 */
enum class SellerErrorCode(
    override val code: Int,
    override val message: String,
    override val status: Int,
) : ErrorCode {

    SELLER_NOT_FOUND(10001, "셀러를 찾을 수 없습니다.", 404),
    // TODO: 도메인 기능 구현하며 추가
}
