package com.aechak.application.sns.usecase

import com.aechak.application.sns.usecase.command.LikeCommentCommand
import com.aechak.application.sns.usecase.command.LikePostCommand
import com.aechak.application.sns.usecase.result.SnsLikeResult
import com.aechak.application.sns.usecase.result.SnsLikerListResult

interface SnsLikeUseCase {
    // 게시물 좋아요를 켜고 지금의 좋아요 수를 돌려준다. 이미 좋아요했으면 그대로 둔다
    fun likePost(command: LikePostCommand): SnsLikeResult

    // 게시물 좋아요를 끄고 지금의 좋아요 수를 돌려준다. 켜기와 같은 게시물 판정을 거친다
    fun unlikePost(command: LikePostCommand): SnsLikeResult

    // 게시물 좋아요한 사람을 최근 100명까지 돌려준다. 보는 사람과 차단 관계인 사용자는 빼고 채운다
    fun getPostLikers(command: LikePostCommand): SnsLikerListResult

    // 댓글 좋아요를 켜고 지금의 좋아요 수를 돌려준다. 게시물 판정 뒤 댓글이 보는 사람에게 보여야 한다
    fun likeComment(command: LikeCommentCommand): SnsLikeResult

    // 댓글 좋아요를 끄고 지금의 좋아요 수를 돌려준다. 켜기와 같은 판정을 거친다
    fun unlikeComment(command: LikeCommentCommand): SnsLikeResult

    // 댓글 좋아요한 사람을 최근 100명까지 돌려준다. 게시물 좋아요한 사람과 같은 규칙이다
    fun getCommentLikers(command: LikeCommentCommand): SnsLikerListResult
}
