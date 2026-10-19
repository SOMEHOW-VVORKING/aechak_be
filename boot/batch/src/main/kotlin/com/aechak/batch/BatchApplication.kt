package com.aechak.batch

import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.TypeExcludeFilter
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType

/**
 * 배치 실행 모듈 진입점. 웹 응답 규격(web-common)을 모른다 —
 * 예외는 HTTP 변환 대신 errorCode 기준 스킵/재시도로 소비한다.
 *
 * 선별 스캔이다 — 루트(com.aechak) 스캔은 인증·SMS·Redis 등 api 전용 조립까지 요구해 부팅이 깨진다.
 * application 패키지를 추가로 쓰게 되면 그 패키지의 Facade가 요구하는 포트 어댑터(infra)와 설정도 함께 늘린다.
 * 전역 lazy-init은 쓰지 않는다 — @Scheduled 빈이 지연 생성되면 스케줄 자체가 등록되지 않는다.
 */
@SpringBootApplication
@ComponentScan(
    basePackages = [
        "com.aechak.batch", // 이 모듈 — 스케줄과 조립
        "com.aechak.infra.kafka", // 아웃박스 스위퍼
        "com.aechak.pii", // PII 암호화 엔진·키 조립(PiiCryptoConfig) — 주문 배송지 스냅샷 암·복호가 경유
        "com.aechak.application.pii", // 암호문 Base64 왕복 부품(PiiStringCodec)
        "com.aechak.application.payment", // 결제 확정 본체·대사
        "com.aechak.application.order", // 확정의 주문 전이(OrderUseCase) — 장바구니 파사드가 딸려와 file·s3가 필요하다
        "com.aechak.application.user.address", // OrderFacade의 배송지 의존
        "com.aechak.application.user.point", // OrderFacade의 적립금 의존
        "com.aechak.application.settlement", // OrderFacade의 구매확정 정산 원장 의존
        "com.aechak.application.user.user", // 적립금·배송지 파사드의 UserUseCase 의존
        "com.aechak.application.user.term", // UserFacade의 약관 동의 의존
        "com.aechak.application.file", // CartFacade의 FileUseCase 의존
        "com.aechak.infra.persistence", // JPA 어댑터 (QuerydslConfig 포함)
        "com.aechak.infra.client.payment", // 포트원 어댑터·대사 소스
        "com.aechak.infra.s3", // FileStorage 어댑터
    ],
    excludeFilters = [
        // @ComponentScan을 직접 선언하면 @SpringBootApplication의 메타 필터가 가려진다 — 기본 두 개를 복원한다.
        ComponentScan.Filter(type = FilterType.CUSTOM, classes = [TypeExcludeFilter::class]),
        ComponentScan.Filter(type = FilterType.CUSTOM, classes = [AutoConfigurationExcludeFilter::class]),
        // 어드민 전용 구현체 제외(seller-api와 같은 규약). 바이너리 이름의 '$'까지 받도록 .*로 받는다.
        ComponentScan.Filter(type = FilterType.REGEX, pattern = ["com\\.aechak\\.application\\..*\\.Admin.*"]),
        // 셀러센터 전용 주문 유스케이스 제외. order BC를 스캔하지만 이 묶음은 셀러 자격(application.seller)을 요구해
        // 조립하면 배치가 쓰지도 않는 의존을 끌고 온다.
        ComponentScan.Filter(type = FilterType.REGEX, pattern = ["com\\.aechak\\.application\\.order\\.seller\\..*"]),
        // 셀러센터 전용 정산 조회 제외. 구매확정 원장 생성을 위해 settlement BC를 스캔하지만 이 묶음은
        // 셀러 자격(application.seller)을 요구해 조립하면 배치가 쓰지도 않는 의존을 끌고 온다.
        ComponentScan.Filter(type = FilterType.REGEX, pattern = ["com\\.aechak\\.application\\.settlement\\.seller\\..*"]),
    ],
)
class BatchApplication

fun main(args: Array<String>) {
    runApplication<BatchApplication>(*args)
}
