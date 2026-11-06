package com.aechak.application.user.sanction.usecase.command

/** 운영자 프로필 초기화 입력 — 이미지와 소개글을 비우는 고정 조치라 대상만 받는다. */
data class ResetMemberProfileCommand(
    val adminId: Long,
    val targetUserId: Long,
)
