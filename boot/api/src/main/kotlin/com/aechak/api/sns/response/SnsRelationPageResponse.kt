package com.aechak.api.sns.response

import com.aechak.application.sns.usecase.result.SnsRelationItemResult
import com.aechak.application.support.CursorPageResult
import java.time.LocalDateTime

/** 차단, 뮤트 관리 목록. totalCount는 첫 페이지에만 싣는다 */
data class SnsRelationPageResponse(
    val items: List<SnsRelationItemResponse>,
    val totalCount: Long?,
    val nextCursor: String?,
    val hasNext: Boolean,
) {
    data class SnsRelationItemResponse(
        val publicId: String,
        val nickname: String,
        val profileImageUrl: String?,
        val createdAt: LocalDateTime,
    )

    companion object {
        fun from(page: CursorPageResult<SnsRelationItemResult>): SnsRelationPageResponse =
            SnsRelationPageResponse(
                items = page.items.map { SnsRelationItemResponse(it.publicId, it.nickname, it.profileImageUrl, it.createdAt) },
                totalCount = page.totalCount,
                nextCursor = page.nextCursor,
                hasNext = page.hasNext,
            )
    }
}
