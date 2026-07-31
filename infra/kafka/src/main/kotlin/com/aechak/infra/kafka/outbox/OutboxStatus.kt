package com.aechak.infra.kafka.outbox

/** 모든 전이는 PENDING에서만 출발(조건부 UPDATE) */
object OutboxStatus {
    const val PENDING = 0
    const val PUBLISHED = 1
    const val DEAD = 2

    // 만료됐지만 자동 종결(취소)이 정의 안 된 행. 사람이 원인 해소 후 PENDING으로 수동 재개
    const val HOLD = 3
}
