package com.aechak.api.sns.response

import com.aechak.application.sns.usecase.result.SnsCommentThreadResult
import io.swagger.v3.oas.annotations.media.Schema

data class SnsCommentThreadResponse(
    val comment: SnsCommentItemResponse,
    @field:Schema(description = "comment가 대댓글이면 그 최상위 댓글(안내 문구 항목일 수 있다). 최상위 댓글이면 null")
    val parent: SnsCommentItemResponse?,
) {
    companion object {
        fun from(result: SnsCommentThreadResult): SnsCommentThreadResponse =
            SnsCommentThreadResponse(SnsCommentItemResponse.from(result.comment), result.parent?.let(SnsCommentItemResponse::from))
    }
}
