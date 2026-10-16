package com.aechak.application.support

object CursorPageSize {
    const val DEFAULT = 20

    const val MIN = 1L
    const val MAX = 100L

    fun fetchLimit(size: Int): Int = size + 1
}
