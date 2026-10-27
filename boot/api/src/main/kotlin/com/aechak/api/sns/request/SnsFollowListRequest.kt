package com.aechak.api.sns.request

import com.aechak.application.sns.port.SnsFollowDirection
import com.aechak.application.sns.usecase.query.SnsFollowListQuery
import com.aechak.application.support.CursorPageSize
import org.hibernate.validator.constraints.Range

/** nickname은 검증하지 않는다. 정규화와 길이 판정은 application이 한다 */
data class SnsFollowListRequest(
    val cursor: String? = null,
    @field:Range(
        min = CursorPageSize.MIN,
        max = CursorPageSize.MAX,
        message = "size는 {min}~{max} 사이여야 합니다.",
    )
    val size: Int = CursorPageSize.DEFAULT,
    val nickname: String? = null,
) {
    fun toQuery(
        viewerId: Long,
        ownerPublicId: String,
        direction: SnsFollowDirection,
    ) = SnsFollowListQuery(
        viewerId = viewerId,
        ownerPublicId = ownerPublicId,
        direction = direction,
        cursor = cursor,
        size = size,
        rawNickname = nickname,
    )
}
