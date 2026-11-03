package com.aechak.api.sns.request

import com.aechak.application.sns.usecase.command.CreatePostCommand
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import io.swagger.v3.oas.annotations.media.Schema

/** 게시 요청. 모든 필드를 생략할 수 있고 생략하면 null이나 빈 배열과 같다 */
data class CreatePostRequest(
    @field:Schema(description = "캡션. NFC 정규화와 앞뒤 공백 정리 뒤 코드 포인트 2,200자까지", example = "오늘 코코랑 한강 산책")
    val caption: String? = null,
    @field:Schema(description = "SNS_POST_IMAGE로 올린 tmp key 0개부터 10개까지. 배열 순서가 보여주는 순서다")
    val photoKeys: List<String> = emptyList(),
    @field:Schema(description = "영상 하나. 아직 받지 않아 보내면 90001이다")
    val video: PostVideoRequest? = null,
    @field:Schema(description = "해시태그 이름. 아직 받지 않아 비어 있지 않으면 90001이다")
    val hashtags: List<String> = emptyList(),
) {
    fun toCommand(
        userId: Long,
        idempotencyKey: String,
    ): CreatePostCommand {
        // 해시태그와 영상은 아직 받지 않는다
        if (hashtags.isNotEmpty()) throw BusinessException(CommonErrorCode.INVALID_REQUEST, detail = "hashtags: 아직 해시태그를 받지 않습니다.")
        if (video != null) throw BusinessException(CommonErrorCode.INVALID_REQUEST, detail = "video: 아직 영상을 받지 않습니다.")
        return CreatePostCommand(userId = userId, idempotencyKey = idempotencyKey, caption = caption, photoKeys = photoKeys)
    }
}

data class PostVideoRequest(
    @field:Schema(description = "SNS_POST_VIDEO로 올린 tmp key")
    val key: String,
    @field:Schema(description = "SNS_POST_IMAGE로 올린 썸네일 tmp key")
    val thumbnailKey: String? = null,
    @field:Schema(description = "영상 길이(밀리초)")
    val durationMs: Int,
)
