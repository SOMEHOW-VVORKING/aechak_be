package com.aechak.api.sns.request

import com.aechak.application.sns.port.SnsRelationKind
import com.aechak.application.sns.usecase.query.SnsRelationListQuery
import com.aechak.application.support.CursorPageSize
import org.hibernate.validator.constraints.Range

data class SnsRelationListRequest(
    val cursor: String? = null,
    @field:Range(
        min = CursorPageSize.MIN,
        max = CursorPageSize.MAX,
        message = "size는 {min}~{max} 사이여야 합니다.",
    )
    val size: Int = CursorPageSize.DEFAULT,
) {
    fun toQuery(
        userId: Long,
        kind: SnsRelationKind,
    ) = SnsRelationListQuery(userId = userId, kind = kind, cursor = cursor, size = size)
}
