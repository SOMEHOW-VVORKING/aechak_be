package com.aechak.application.support

/** 오프셋 페이지네이션 공용 입력 — of()가 형식 오류를 보정한다(음수 page→0, size는 1~maxSize 절삭). */
data class PageQuery(
    val page: Int,
    val size: Int,
) {
    init {
        require(page >= 0) { "page는 0 이상이어야 합니다." }
        require(size >= 1) { "size는 1 이상이어야 합니다." }
    }

    companion object {
        const val DEFAULT_MAX_SIZE = 100

        @JvmStatic
        @JvmOverloads
        fun of(
            page: Int,
            size: Int,
            maxSize: Int = DEFAULT_MAX_SIZE,
        ): PageQuery = PageQuery(maxOf(page, 0), size.coerceIn(1, maxSize))
    }
}
