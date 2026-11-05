package com.aechak.application.sns.port.view

/** 저장됨 탭이 고른 저장 하나. saveId가 keyset이고 postId는 저장한 게시물이다 */
data class SnsSaveView(
    val saveId: Long,
    val postId: Long,
)
