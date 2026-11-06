package com.aechak.api.sns.request

import com.aechak.application.sns.usecase.query.SnsHashtagPostsQuery
import com.aechak.application.sns.usecase.query.SnsSearchQuery
import com.aechak.application.sns.usecase.query.SnsSearchSort
import com.aechak.application.support.CursorPageSize
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size
import org.hibernate.validator.constraints.Range

/** keyword는 검색창 입력값 그대로다. 앞의 #와 공백 정리는 application이 하고 정리한 값이 비면 빈 결과다 */
data class SnsSearchRequest(
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "산책")
    @field:Size(max = SnsSearchQuery.MAX_LENGTH, message = "검색어가 너무 깁니다.")
    val keyword: String = "",
    @field:Schema(allowableValues = ["POPULAR", "LATEST"], defaultValue = "POPULAR")
    val sort: String = SnsSearchSort.POPULAR.name,
    val cursor: String? = null,
    @field:Range(
        min = CursorPageSize.MIN,
        max = CursorPageSize.MAX,
        message = "size는 {min}~{max} 사이여야 합니다.",
    )
    val size: Int = CursorPageSize.DEFAULT,
) {
    fun toQuery(viewerId: Long?) = SnsSearchQuery(viewerId, keyword, parseSort(sort), cursor, size)
}

data class SnsHashtagPostsRequest(
    @field:Schema(allowableValues = ["POPULAR", "LATEST"], defaultValue = "POPULAR")
    val sort: String = SnsSearchSort.POPULAR.name,
    val cursor: String? = null,
    @field:Range(
        min = CursorPageSize.MIN,
        max = CursorPageSize.MAX,
        message = "size는 {min}~{max} 사이여야 합니다.",
    )
    val size: Int = CursorPageSize.DEFAULT,
) {
    fun toQuery(
        viewerId: Long?,
        name: String,
    ) = SnsHashtagPostsQuery(viewerId, name, parseSort(sort), cursor, size)
}

private fun parseSort(value: String): SnsSearchSort =
    SnsSearchSort.entries.firstOrNull { it.name == value } ?: throw BusinessException(CommonErrorCode.INVALID_REQUEST)
