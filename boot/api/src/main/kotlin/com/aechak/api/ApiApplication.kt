package com.aechak.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * API 실행 모듈 진입점. 컴포넌트 스캔을 루트 패키지로 잡아
 * web-common(전역 예외 핸들러)·application·infra 빈을 모두 포함한다.
 *
 * 도메인별 Controller + request/response dto는 {도메인}/ 패키지에 둔다 (예: user/).
 *
 * TODO: config/ — TraceIdFilter 등록(FilterRegistrationBean), 보안, Jackson 설정
 * TODO: consumer/ — Kafka 컨슈머 (컨트롤러와 동급의 진입점 — UseCase만 호출)
 */
@SpringBootApplication(scanBasePackages = ["com.aechak"])
class ApiApplication

fun main(args: Array<String>) {
    runApplication<ApiApplication>(*args)
}
