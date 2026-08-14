package com.aechak.application.product.like.usecase.query

/** 내 찜 목록 조회 입력 (커서 페이지네이션) */
data class LikedProductListQuery(
    val cursor: String? = null,
    val size: Int = 20,
) {
    init {
        require(size in SIZE_RANGE) { "size는 $SIZE_RANGE 범위 안에 있어야 합니다." }
    }

    companion object {
        val SIZE_RANGE = 1..100
    }
}
