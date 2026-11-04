package com.aechak.application.sns.usecase

import com.aechak.application.sns.usecase.command.CreatePostCommand
import com.aechak.application.sns.usecase.command.UpdatePostCommand
import com.aechak.application.sns.usecase.result.CreatePostResult
import com.aechak.application.sns.usecase.result.SnsPostDetailResult

interface SnsPostUseCase {
    // 게시물을 만든다. 같은 작성자가 같은 멱등키로 만든 게시물이 있으면 그 게시물을 돌려준다
    fun createPost(command: CreatePostCommand): CreatePostResult

    // 작성자가 캡션과 해시태그를 통째로 바꾼다. 바뀐 것이 없으면 수정 시각을 그대로 둔다
    fun updatePost(command: UpdatePostCommand)

    // 게시물 상세. viewerId가 null이면 비로그인이다
    fun getPost(
        publicId: String,
        viewerId: Long?,
    ): SnsPostDetailResult
}
