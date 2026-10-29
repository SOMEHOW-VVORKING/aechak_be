package com.aechak.infra.kafka

import com.aechak.message.review.ReviewSummaryRequestedMessage

/**
 * 토픽 이름 규칙 관리
 */
object Topics {
    private const val PREFIX = "aechak"
    private const val SUFFIX = "events"
    private const val DLT_SUFFIX = ".dlt"

    const val ORDER = "$PREFIX.order.$SUFFIX"
    const val ORDER_DLT = "$ORDER$DLT_SUFFIX"

    const val REVIEW = "$PREFIX.review.$SUFFIX"
    const val REVIEW_DLT = "$REVIEW$DLT_SUFFIX"

    /**
     * 검수 요청(백엔드 → AI 서버). 상품·리뷰가 같은 토픽을 쓴다.
     * 상품 도메인 토픽과 섞지 않는 이유는 판정 한 건이 막힐 때 그 도메인 이벤트까지 멈추기 때문이다.
     */
    const val INSPECTION = "$PREFIX.inspection.$SUFFIX"
    const val INSPECTION_DLT = "$INSPECTION$DLT_SUFFIX"

    /** 판정 결과(AI 서버 → 백엔드). 보내는 쪽이 파이썬이라 아웃박스를 타지 않는다. */
    const val VERDICT = "$PREFIX.verdict.$SUFFIX"
    const val VERDICT_DLT = "$VERDICT$DLT_SUFFIX"

    /** 리뷰 요약 요청(백엔드 → AI 서버). 이름은 AI 서버가 정한 것이라 events 규칙을 따르지 않음 */
    const val REVIEW_SUMMARY_REQUEST = "$PREFIX.summary.requests"

    /** 리뷰 요약 결과(AI 서버 → 백엔드) */
    const val REVIEW_SUMMARY_RESULT = "$PREFIX.summary.results"
    const val REVIEW_SUMMARY_RESULT_DLT = "$REVIEW_SUMMARY_RESULT$DLT_SUFFIX"

    /** 릴레이가 아웃박스 행의 aggregate_type으로 목적지 토픽 이름을 만든다. */
    fun of(aggregateType: String) =
        if (aggregateType == ReviewSummaryRequestedMessage.AGGREGATE_TYPE) REVIEW_SUMMARY_REQUEST else "$PREFIX.$aggregateType.$SUFFIX"

    /** 처리에 실패한 메시지를 격리할 토픽 이름. 원본 토픽 이름 뒤에 접미사만 붙인다. */
    fun dltOf(topic: String) = "$topic$DLT_SUFFIX"
}
