package com.aechak.infra.kafka

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

    /** 릴레이가 아웃박스 행의 aggregate_type으로 목적지 토픽 이름을 만든다. */
    fun of(aggregateType: String) = "$PREFIX.$aggregateType.$SUFFIX"

    /** 처리에 실패한 메시지를 격리할 토픽 이름. 원본 토픽 이름 뒤에 접미사만 붙인다. */
    fun dltOf(topic: String) = "$topic$DLT_SUFFIX"
}
