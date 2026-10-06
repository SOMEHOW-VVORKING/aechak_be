package com.aechak.api.review.request

/**
 * 리뷰 내용 길이 제약. 형식 규칙이라 요청 계층이 단일 원본이다.
 * 별점(1~5)과 사진 장수 상한은 도메인 불변식이라 여기 두지 않는다(Review가 소유).
 */
object ReviewConstraints {
    const val CONTENT_MIN = 10
    const val CONTENT_MAX = 1000
}
