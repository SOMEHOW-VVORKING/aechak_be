package com.aechak.application.sns.usecase

import com.aechak.application.sns.usecase.command.BlockUserCommand
import com.aechak.application.sns.usecase.command.MuteUserCommand
import com.aechak.application.sns.usecase.result.SnsProfileResult

interface SnsUseCase {
    /** 다른 사용자의 SNS 프로필. viewerId는 비로그인이면 null이다. */
    fun getProfile(
        publicId: String,
        viewerId: Long?,
    ): SnsProfileResult

    /** 내 SNS 프로필 */
    fun getMyProfile(userId: Long): SnsProfileResult

    /** 차단(멱등 켜기) */
    fun block(command: BlockUserCommand)

    /** 차단 해제(멱등 끄기). 내가 건 차단만 지운다. */
    fun unblock(command: BlockUserCommand)

    /** 뮤트(멱등 켜기). 팔로우와 수는 바꾸지 않는다. */
    fun mute(command: MuteUserCommand)

    /** 뮤트 해제(멱등 끄기) */
    fun unmute(command: MuteUserCommand)
}
