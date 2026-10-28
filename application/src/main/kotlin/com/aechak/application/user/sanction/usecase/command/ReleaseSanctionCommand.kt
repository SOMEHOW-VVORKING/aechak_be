package com.aechak.application.user.sanction.usecase.command

/** 제재 해제 입력 — 고정 어휘가 없는 조치라 상세 사유와 메모만 받는다. */
data class ReleaseSanctionCommand(
    val adminId: Long,
    val targetUserId: Long,
    val reasonDetail: String?,
    val memo: String?,
)
