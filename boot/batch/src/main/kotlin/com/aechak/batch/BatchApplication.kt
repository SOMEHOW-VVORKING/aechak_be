package com.aechak.batch

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * 배치 실행 모듈 진입점. 웹 응답 규격(web-common)을 모른다 —
 * 예외는 HTTP 변환 대신 errorCode 기준 스킵/재시도로 소비한다.
 *
 * TODO: job/{도메인}/ — Job/Step 정의 (ItemProcessor 등에서 UseCase 호출)
 * TODO: support/BusinessSkipPolicy — BusinessException → errorCode 로깅 + 스킵 판단
 */
@SpringBootApplication(scanBasePackages = ["com.aechak"])
class BatchApplication

fun main(args: Array<String>) {
    runApplication<BatchApplication>(*args)
}
