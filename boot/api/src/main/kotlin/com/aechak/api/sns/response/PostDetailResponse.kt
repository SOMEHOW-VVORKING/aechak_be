package com.aechak.api.sns.response

import com.aechak.api.sns.response.SnsProfileResponse.SnsPetResponse
import com.aechak.application.sns.usecase.result.SnsPostDetailResult
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

data class PostDetailResponse(
    val publicId: String,
    val author: PostAuthorResponse,
    @field:Schema(description = "게시할 때의 대표 펫. 품종과 생년월은 지금 값이다. 펫이 없었거나 그 펫을 지웠으면 null")
    val pet: SnsPetResponse?,
    @field:Schema(description = "금지어를 가린 뒤의 캡션. 없으면 null")
    val caption: String?,
    @field:Schema(description = "작성자가 단 순서대로 정규화한 해시태그 이름(# 없음)")
    val hashtags: List<String>,
    @field:Schema(description = "사진 표시용 URL. 첫 항목이 대표 이미지다. 사진 게시물이 아니면 빈 배열")
    val photoUrls: List<String>,
    @field:Schema(description = "영상 게시물이 아니면 null. photoUrls가 비고 video가 null이면 텍스트 게시물이다")
    val video: PostVideoResponse?,
    val likeCount: Long,
    val commentCount: Long,
    val repostCount: Long,
    @field:Schema(description = "캡션이나 해시태그를 고친 적이 있으면 true")
    val edited: Boolean,
    val createdAt: LocalDateTime,
    @get:JsonProperty("isMine")
    val isMine: Boolean,
    val following: Boolean,
    val mutedByMe: Boolean,
) {
    data class PostAuthorResponse(
        val publicId: String,
        val nickname: String,
        val profileImageUrl: String?,
    )

    data class PostVideoResponse(
        val url: String,
        val thumbnailUrl: String,
        val durationMs: Int,
    )

    companion object {
        fun from(result: SnsPostDetailResult): PostDetailResponse =
            PostDetailResponse(
                publicId = result.publicId,
                author = PostAuthorResponse(result.author.publicId, result.author.nickname, result.author.profileImageUrl),
                pet = result.pet?.let(SnsPetResponse::from),
                caption = result.caption,
                hashtags = result.hashtags,
                photoUrls = result.photoUrls,
                video = result.video?.let { PostVideoResponse(it.url, it.thumbnailUrl, it.durationMs) },
                likeCount = result.likeCount,
                commentCount = result.commentCount,
                repostCount = result.repostCount,
                edited = result.edited,
                createdAt = result.createdAt,
                isMine = result.isMine,
                following = result.following,
                mutedByMe = result.mutedByMe,
            )
    }
}
