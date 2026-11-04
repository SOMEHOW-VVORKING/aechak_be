package com.aechak.api.sns.request

import com.aechak.application.sns.usecase.command.CreatePostCommand
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.Min

/** 게시 요청. 모든 필드를 생략할 수 있고 생략하면 null이나 빈 배열과 같다 */
data class CreatePostRequest(
    @field:Schema(description = "캡션. NFC 정규화와 앞뒤 공백 정리 뒤 코드 포인트 2,200자까지", example = "오늘 코코랑 한강 산책")
    val caption: String? = null,
    @field:Schema(description = "SNS_POST_IMAGE로 올린 tmp key 0개부터 10개까지. 배열 순서가 보여주는 순서다")
    val photoKeys: List<String> = emptyList(),
    @field:Valid
    @field:Schema(description = "영상 하나. photoKeys와 함께 보내면 130404다")
    val video: PostVideoRequest? = null,
    @field:Schema(description = "해시태그 이름. 앞의 #과 앞뒤 공백을 떼고 NFC 정규화와 영문 소문자화를 하며 같은 태그는 하나로 합쳐 30개까지")
    val hashtags: List<String> = emptyList(),
) {
    fun toCommand(
        userId: Long,
        idempotencyKey: String,
    ): CreatePostCommand =
        CreatePostCommand(
            userId = userId,
            idempotencyKey = idempotencyKey,
            caption = caption,
            photoKeys = photoKeys,
            hashtags = hashtags,
            video = video?.let { CreatePostCommand.Video(it.key, it.thumbnailKey, it.durationMs) },
        )
}

data class PostVideoRequest(
    @field:Schema(description = "SNS_POST_VIDEO로 올린 tmp key")
    val key: String,
    @field:Schema(description = "SNS_POST_IMAGE로 올린 썸네일 tmp key. 없거나 null이면 90001이 아니라 130404다")
    val thumbnailKey: String? = null,
    @field:Min(value = 1, message = "durationMs는 1 이상이어야 합니다.")
    @field:Schema(description = "FE가 영상 메타데이터에서 읽은 길이(밀리초). 60,000을 넘으면 130407이다")
    val durationMs: Int,
)
