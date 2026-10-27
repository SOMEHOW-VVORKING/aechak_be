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

    // 올림을 분자 더하기로 하면 totalCount가 Long 최댓값 근처에서 넘친다 — 나머지로 올린다.
    // Int 범위를 넘는 페이지 수는 상한에서 멈춘다. 줄여 담으면 음수로 뒤집혀 첫 페이지에서도 hasNext가 거짓이 된다.
    // 상한에 걸린 구간에서는 hasNext도 함께 일찍 끊긴다 — 페이지 수가 Int로 표현되지 않는 규모는 지원하지 않는다.
    val totalPages: Int
        get() = (totalCount / size + if (totalCount % size == 0L) 0L else 1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    // Long 덧셈 — Int.MAX_VALUE page 요청에서 page + 1이 음수로 넘쳐 마지막 페이지가 hasNext=true로 뒤집힌다
    val hasNext: Boolean get() = page.toLong() + 1 < totalPages.toLong()

    fun <R> map(transform: (T) -> R): OffsetPageResult<R> =
        OffsetPageResult(
            items = items.map(transform),
            totalCount = totalCount,
            page = page,
            size = size,
        )
}
