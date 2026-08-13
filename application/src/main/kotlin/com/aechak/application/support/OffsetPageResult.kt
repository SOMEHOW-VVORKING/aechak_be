package com.aechak.application.support

/** 오프셋 페이지네이션 공용 출력 래퍼 — 셀러센터처럼 페이지 점프가 필요한 목록에 쓴다 */
data class OffsetPageResult<T>(
    val items: List<T>,
    val totalCount: Long,
    val page: Int,
    val size: Int,
) {
    init {
        require(page >= 0) { "page는 0 이상이어야 합니다." }
        require(size > 0) { "size는 양수여야 합니다." }
        require(totalCount >= 0) { "totalCount는 음수일 수 없습니다." }
    }

    val totalPages: Int get() = ((totalCount + size - 1) / size).toInt()

    val hasNext: Boolean get() = page + 1 < totalPages

    fun <R> map(transform: (T) -> R): OffsetPageResult<R> =
        OffsetPageResult(
            items = items.map(transform),
            totalCount = totalCount,
            page = page,
            size = size,
        )
}
