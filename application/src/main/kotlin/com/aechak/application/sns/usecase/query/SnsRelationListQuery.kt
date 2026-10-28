package com.aechak.application.sns.usecase.query

import com.aechak.application.sns.port.SnsRelationKind
import com.aechak.application.support.CursorPageSize

/** 차단, 뮤트 관리 목록 입력. userId는 principal이다 */
data class SnsRelationListQuery(
    val userId: Long,
    val kind: SnsRelationKind,
    val cursor: String?,
    val size: Int = CursorPageSize.DEFAULT,
) {
    init {
        require(size in CursorPageSize.MIN..CursorPageSize.MAX) { "size는 ${CursorPageSize.MIN}~${CursorPageSize.MAX} 범위 안에 있어야 합니다." }
    }
}
