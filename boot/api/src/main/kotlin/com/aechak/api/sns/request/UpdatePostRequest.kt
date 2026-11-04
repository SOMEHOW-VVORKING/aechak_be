package com.aechak.api.sns.request

import com.aechak.application.sns.usecase.command.UpdatePostCommand
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

/** 수정 요청. 두 값을 함께 보내 통째로 바꾸고 하나라도 빠지면 90001이다 */
data class UpdatePostRequest(
    // 빠진 caption을 null로 읽으면 캡션 삭제로 저장되므로 필수로 받는다
    @JsonProperty(required = true)
    @field:Schema(description = "캡션. null이나 공백뿐이면 캡션을 지운다", nullable = true)
    val caption: String?,
    @JsonProperty(required = true)
    @field:Schema(description = "해시태그 이름. 게시와 같은 규칙이고 빈 배열이면 해시태그를 모두 뺀다. 순서만 바꿔도 수정으로 본다")
    val hashtags: List<String>,
) {
    fun toCommand(
        userId: Long,
        publicId: String,
    ): UpdatePostCommand = UpdatePostCommand(userId = userId, publicId = publicId, caption = caption, hashtags = hashtags)
}
