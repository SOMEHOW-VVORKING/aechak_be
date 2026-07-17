package com.aechak.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * API 실행 모듈 진입점. 컴포넌트 스캔을 루트 패키지로 잡아
 * web-common(전역 예외 핸들러)·application·infra 빈을 모두 포함한다.
 *
 * 패키지 규칙:
 * - {도메인}/ — 도메인별 Controller + request/response dto (예: user/, auth/)
 * - security/ — 인증·인가 전부(체인 조립·상태 필터·JWT 키/코덱·프로퍼티). "누가 무엇을 쓸 수 있나"는 여기서 읽는다
 * - config/ — 그 외 기술 조립 (JPA 스캔 등)
 *
 * TODO: config/ — TraceIdFilter 등록(FilterRegistrationBean), Jackson 설정
 * TODO: consumer/ — Kafka 컨슈머 (컨트롤러와 동급의 진입점 — UseCase만 호출)
 */
@SpringBootApplication(scanBasePackages = ["com.aechak"])
class ApiApplication

fun main(args: Array<String>) {
    runApplication<ApiApplication>(*args)
}
