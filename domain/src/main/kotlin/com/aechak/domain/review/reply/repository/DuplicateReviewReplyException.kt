package com.aechak.domain.review.reply.repository

/** 같은 리뷰에 이미 답글이 저장된 경우 발생한다. */
class DuplicateReviewReplyException(
    cause: Throwable,
) : RuntimeException(cause)
