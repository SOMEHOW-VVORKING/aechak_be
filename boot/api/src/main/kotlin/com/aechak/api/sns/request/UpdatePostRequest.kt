package com.aechak.api.sns.request

import com.aechak.application.sns.usecase.command.UpdatePostCommand
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

/** 수정 요청. 두 값을 함께 보내 통째로 바꾸고 하나라도 빠지면 90001이다 */
data class UpdatePostRequest(
    // 빠진 caption을 null로 읽으면 캡션 삭제로 저장되므로 필수로 받는다
    @JsonProperty(required = true)
    @field:Schema(description = "캡션. null이나 공백뿐이면 캡션을 지운다", nullable = true)
    val caption: String?,
    @JsonProperty(required = true)
    @field:Schema(description = "해시태그 이름. 아직 받지 않아 비어 있지 않으면 90001이다")
    val hashtags: List<String>,
) {
    fun toCommand(
        userId: Long,
        publicId: String,
    ): UpdatePostCommand {
        // 해시태그는 아직 받지 않는다
        if (hashtags.isNotEmpty()) throw BusinessException(CommonErrorCode.INVALID_REQUEST, detail = "hashtags: 아직 해시태그를 받지 않습니다.")
        return UpdatePostCommand(userId = userId, publicId = publicId, caption = caption)
    }
}
