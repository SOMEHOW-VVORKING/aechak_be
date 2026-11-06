package com.aechak.application.sns.usecase.result

/** 댓글 하나와 그 최상위 댓글. comment가 최상위 댓글이면 parent는 null이다 */
data class SnsCommentThreadResult(
    val comment: SnsCommentItemResult,
    val parent: SnsCommentItemResult?,
)
