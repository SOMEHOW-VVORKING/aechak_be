package com.aechak.batch

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * 배치 실행 모듈 진입점. 웹 응답 규격(web-common)을 모른다 —
 * 예외는 HTTP 변환 대신 errorCode 기준 스킵/재시도로 소비한다.
 *
 * TODO: 잡이 늘어나면 errorCode 기준 스킵 판단을 support/BusinessSkipPolicy로 공용화 (지금은 만료 잡의 skipPolicy 인라인)
 */
@SpringBootApplication(
    scanBasePackages = [
        "com.aechak.batch",
        "com.aechak.application",
        "com.aechak.infra.kafka",
        "com.aechak.infra.persistence",
        "com.aechak.pii",
        "com.aechak.infra.client.payment",
    ],
)
class BatchApplication

fun main(args: Array<String>) {
    runApplication<BatchApplication>(*args)
}
