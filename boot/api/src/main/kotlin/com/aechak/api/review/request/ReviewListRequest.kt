package com.aechak.api.review.request

import com.aechak.application.review.port.ReviewListSort
import com.aechak.application.review.usecase.query.ProductReviewListQuery
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import org.hibernate.validator.constraints.Range

data class ReviewListRequest(
    val sort: String = "latest",
    val photoOnly: Boolean = false,
    val cursor: String? = null,
    @field:Range(
        min = ProductReviewListQuery.SIZE_MIN,
        max = ProductReviewListQuery.SIZE_MAX,
        message = "size는 {min}~{max} 사이여야 합니다.",
    )
    val size: Int = ProductReviewListQuery.DEFAULT_SIZE,
) {
    fun toQuery(productPublicId: String) =
        ProductReviewListQuery(
            productPublicId = productPublicId,
            sort = parseSort(sort),
            photoOnly = photoOnly,
            cursor = cursor,
            size = size,
        )

    private fun parseSort(value: String): ReviewListSort =
        when (value) {
            "latest" -> ReviewListSort.LATEST
            "rating_desc" -> ReviewListSort.RATING_DESC
            else -> throw BusinessException(CommonErrorCode.INVALID_REQUEST)
        }
}
