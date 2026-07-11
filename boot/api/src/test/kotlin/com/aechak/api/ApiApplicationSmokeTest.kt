package com.aechak.api

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

/**
 * 컨텍스트 부팅 스모크 테스트.
 *
 * 전체 스프링 컨텍스트를 띄우면서 domain 모듈 전 엔티티의 Hibernate 매핑 검증과
 * H2 스키마 생성(ddl-auto)이 함께 실행된다 — 컴파일만으로는 잡히지 않는
 * 매핑 오류(@MapsId, @JoinColumn, columnDefinition, UNIQUE 제약 등)를 잡는 최소 안전망.
 * 실 DB 전환 시에도 이 테스트는 내장 H2 기준으로 유지한다.
 */
@SpringBootTest
class ApiApplicationSmokeTest {

    @Test
    fun contextLoads() {
        // 컨텍스트 기동 자체가 검증이다 — 매핑이 깨지면 여기서 실패한다.
    }
}
