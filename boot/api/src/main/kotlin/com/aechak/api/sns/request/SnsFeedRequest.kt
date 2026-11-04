package com.aechak.api.sns.request

import com.aechak.application.sns.usecase.query.SnsFeedQuery
import com.aechak.application.support.CursorPageSize
import org.hibernate.validator.constraints.Range

data class SnsFeedRequest(
    val cursor: String? = null,
    @field:Range(
        min = CursorPageSize.MIN,
        max = CursorPageSize.MAX,
        message = "size는 {min}~{max} 사이여야 합니다.",
    )
    val size: Int = CursorPageSize.DEFAULT,
) {
    fun toQuery(viewerId: Long?) = SnsFeedQuery(viewerId = viewerId, cursor = cursor, size = size)
}
