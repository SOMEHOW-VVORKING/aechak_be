package com.aechak.application.sns.usecase

import com.aechak.application.sns.usecase.command.CreatePostCommand
import com.aechak.application.sns.usecase.command.RepostPostCommand
import com.aechak.application.sns.usecase.command.SavePostCommand
import com.aechak.application.sns.usecase.command.UpdatePostCommand
import com.aechak.application.sns.usecase.result.CreatePostResult
import com.aechak.application.sns.usecase.result.SnsHashtagSearchResult
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

    // 해시태그 자동완성. 검색어가 글자 규칙을 어겨도 거부하지 않고 정리한 검색어의 상태로 알린다
    fun searchHashtags(
        query: String?,
        size: Int,
    ): SnsHashtagSearchResult

    // 원글을 리포스트한다. 이미 리포스트 중이면 그대로 둔다
    fun repost(command: RepostPostCommand)

    // 리포스트를 취소한다. 게시물은 publicId로만 찾고 상태와 관계는 보지 않는다
    fun unrepost(command: RepostPostCommand)

    // 게시물을 저장한다. 이미 저장 중이면 그대로 둔다
    fun save(command: SavePostCommand)

    // 저장을 취소한다. 게시물은 publicId로만 찾고 상태와 관계는 보지 않는다
    fun unsave(command: SavePostCommand)
}
