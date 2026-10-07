package com.aechak.application.support

data class CursorPageResult<T>(
    val items: List<T>,
    val totalCount: Long?,
    val nextCursor: String?,
    val hasNext: Boolean,
) {
    init {
        require((nextCursor != null) == hasNext) { "nextCursor와 hasNext의 값이 다릅니다." }
    }

    fun <R> map(transform: (T) -> R): CursorPageResult<R> = CursorPageResult(items.map(transform), totalCount, nextCursor, hasNext)

    companion object {
        fun <T> of(
            fetched: List<T>,
            size: Int,
            totalCount: Long? = null,
            encodeCursor: (T) -> String,
        ): CursorPageResult<T> {
            require(size > 0) { "size는 양수여야 합니다." }
            val hasNext = fetched.size > size
            val items = if (hasNext) fetched.take(size) else fetched
            return CursorPageResult(
                items = items,
                totalCount = totalCount,
                nextCursor = if (hasNext) encodeCursor(items.last()) else null,
                hasNext = hasNext,
            )
        }
    }
}
