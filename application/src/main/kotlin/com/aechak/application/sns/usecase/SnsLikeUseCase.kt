package com.aechak.application.sns.usecase

import com.aechak.application.sns.usecase.command.LikePostCommand
import com.aechak.application.sns.usecase.result.SnsLikeResult

interface SnsLikeUseCase {
    // 게시물 좋아요를 켜고 지금의 좋아요 수를 돌려준다. 이미 좋아요했으면 그대로 둔다
    fun likePost(command: LikePostCommand): SnsLikeResult

    // 게시물 좋아요를 끄고 지금의 좋아요 수를 돌려준다. 켜기와 같은 게시물 판정을 거친다
    fun unlikePost(command: LikePostCommand): SnsLikeResult
}
