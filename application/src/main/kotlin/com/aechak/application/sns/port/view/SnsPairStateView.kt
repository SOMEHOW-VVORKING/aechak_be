package com.aechak.application.sns.port.view

/** 보는 사람과 대상 한 쌍의 관계 상태 */
data class SnsPairStateView(
    val blockedByMe: Boolean,
    val blockedByTarget: Boolean,
    val mutedByMe: Boolean,
    val following: Boolean,
) {
    companion object {
        val NONE = SnsPairStateView(blockedByMe = false, blockedByTarget = false, mutedByMe = false, following = false)
    }
}
