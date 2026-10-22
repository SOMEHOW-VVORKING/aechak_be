package com.aechak.application.sns.usecase

import com.aechak.application.sns.usecase.command.BlockUserCommand
import com.aechak.application.sns.usecase.command.FollowUserCommand
import com.aechak.application.sns.usecase.command.MuteUserCommand
import com.aechak.application.sns.usecase.query.SnsRelationListQuery
import com.aechak.application.sns.usecase.result.SnsProfileResult
import com.aechak.application.sns.usecase.result.SnsRelationItemResult
import com.aechak.application.support.CursorPageResult

interface SnsUseCase {
    /** 다른 사용자의 SNS 프로필. viewerId는 비로그인이면 null이다. */
    fun getProfile(
        publicId: String,
        viewerId: Long?,
    ): SnsProfileResult

    /** 내 SNS 프로필 */
    fun getMyProfile(userId: Long): SnsProfileResult

    /** 팔로우(멱등 켜기). 새로 들어갔을 때만 두 사람의 수를 올린다. */
    fun follow(command: FollowUserCommand)

    /** 언팔로우(멱등 끄기). 지웠을 때만 두 사람의 수를 내린다. */
    fun unfollow(command: FollowUserCommand)

    /** 차단(멱등 켜기) */
    fun block(command: BlockUserCommand)

    /** 차단 해제(멱등 끄기). 내가 건 차단만 지운다. */
    fun unblock(command: BlockUserCommand)

    /** 뮤트(멱등 켜기). 팔로우와 수는 바꾸지 않는다. */
    fun mute(command: MuteUserCommand)

    /** 뮤트 해제(멱등 끄기) */
    fun unmute(command: MuteUserCommand)

    /** 내 차단이나 뮤트 관리 목록(최근순) */
    fun getMyRelations(query: SnsRelationListQuery): CursorPageResult<SnsRelationItemResult>
}
