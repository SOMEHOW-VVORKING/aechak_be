package com.aechak.api.user.user.request

import com.aechak.application.user.user.usecase.command.SetNicknameCommand

/** 형식 규칙(2~12자·허용 문자)은 도메인이 판정한다(30002) — 여기서 검증하면 90001로 오라벨된다. */
data class NicknameRequest(
    val nickname: String,
) {
    fun toCommand(userId: Long): SetNicknameCommand =
        SetNicknameCommand(
            userId = userId,
            nickname = nickname,
        )
}
