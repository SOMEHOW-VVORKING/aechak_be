package com.aechak.domain.review.review.repository

/** 같은 주문 품목에 이미 리뷰가 저장된 경우 발생한다. */
class DuplicateOrderItemReviewException(
    cause: Throwable,
) : RuntimeException(cause)
