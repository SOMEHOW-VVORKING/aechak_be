package com.aechak.application.sns.usecase.query

import com.aechak.application.support.CursorPageSize

/** 프로필 탭 입력. viewerId가 null이면 비로그인이다 */
data class SnsPostTabQuery(
    val viewerId: Long?,
    val ownerPublicId: String,
    val cursor: String?,
    val size: Int = CursorPageSize.DEFAULT,
) {
    init {
        require(size in CursorPageSize.MIN..CursorPageSize.MAX) { "size는 ${CursorPageSize.MIN}~${CursorPageSize.MAX} 범위 안에 있어야 합니다." }
    }
}
