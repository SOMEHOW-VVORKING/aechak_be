package com.aechak.application.support

/** 커서 페이지네이션 공용 출력 래퍼 */
data class CursorPageResult<T>(
    val items: List<T>,
    val totalCount: Long?,
    val nextCursor: String?,
    val hasNext: Boolean,
) {
    init {
        require((nextCursor != null) == hasNext) { "nextCursor와 hasNext의 값이 다릅니다." }
    }
}
