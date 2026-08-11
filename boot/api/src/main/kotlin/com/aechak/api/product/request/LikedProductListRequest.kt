package com.aechak.api.product.request

import com.aechak.application.product.like.usecase.query.LikedProductListQuery
import org.hibernate.validator.constraints.Range

/** 내 찜 목록 조회 요청 파라미터 */
data class LikedProductListRequest(
    val cursor: String? = null,
    @field:Range(min = 1, max = 100, message = "size는 1~100 사이여야 합니다.")
    val size: Int = 20,
) {
    fun toQuery() =
        LikedProductListQuery(
            cursor = cursor,
            size = size,
        )
}
