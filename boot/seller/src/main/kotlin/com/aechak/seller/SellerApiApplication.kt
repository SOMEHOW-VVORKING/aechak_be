package com.aechak.seller

import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.TypeExcludeFilter
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType

/**
 * 셀러센터 실행 모듈 진입점. api와 달리 선별 스캔이다 —
 * 루트(com.aechak) 스캔은 auth, kafka 등 api 전용 조립까지 요구해 부팅이 깨진다.
 * application 패키지를 추가로 쓰게 되면 그 패키지가 요구하는 포트 어댑터(infra)와 조립(config)도 함께 늘린다.
 *
 * excludeFilters: 어드민 전용 빈은 조립하지 않는다. 심사처럼 운영자만 쓰는 유스케이스가
 * 같은 BC 패키지(application.seller)에 사는데, 스캔은 패키지 단위라 가릴 수 없다.
 * 네이밍 규약(30 §7 "경계는 boot/admin과 Admin* 클래스까지")을 필터로 강제해
 * 어드민이 타 도메인을 새로 부를 때마다 이 목록을 늘리지 않아도 되게 한다.
 */
@SpringBootApplication
@ComponentScan(
    basePackages = [
        "com.aechak.seller", // 이 모듈 — 컨트롤러와 조립
        "com.aechak.webcommon", // 전역 예외 핸들러와 응답 규격
        "com.aechak.websecurity", // JWT 디코더와 상태 필터 부품
        "com.aechak.pii", // PII 암호화 엔진과 키 조립(PiiCryptoConfig)
        "com.aechak.application.pii", // 암호문 Base64 왕복 부품(PiiStringCodec) — 계좌번호 암·복호가 경유
        "com.aechak.application.seller", // 입점 신청 유스케이스
        "com.aechak.application.user.user", // 휴대폰 인증 게이트(requirePhoneVerified)가 UserUseCase 경유
        "com.aechak.application.user.term", // UserFacade의 온보딩 동의 검증
        "com.aechak.application.user.verification", // 전화 인증
        "com.aechak.application.file", // 서류 승격
        "com.aechak.application.product", // 상품 등록·카테고리 조회
        "com.aechak.infra.persistence", // JPA 어댑터 (QuerydslConfig 포함)
        "com.aechak.infra.s3", // FileStorage 어댑터
        "com.aechak.infra.redis", // 인증 코드 저장소
        "com.aechak.infra.client.sms", // SmsSender 어댑터
    ],
    excludeFilters = [
        // @ComponentScan을 직접 선언하면 @SpringBootApplication의 메타 필터가 가려진다 — 기본 두 개를 복원한다.
        // TypeExcludeFilter가 없으면 슬라이스 테스트(@WebMvcTest 등)의 빈 걸러내기가 동작하지 않는다.
        ComponentScan.Filter(type = FilterType.CUSTOM, classes = [TypeExcludeFilter::class]),
        ComponentScan.Filter(type = FilterType.CUSTOM, classes = [AutoConfigurationExcludeFilter::class]),
        // 어드민 전용 구현체 제외. 바이너리 이름에는 중첩 클래스의 '$'가 들어올 수 있어 .*로 받는다.
        ComponentScan.Filter(type = FilterType.REGEX, pattern = ["com\\.aechak\\.application\\..*\\.Admin.*"]),
    ],
)
class SellerApiApplication

fun main(args: Array<String>) {
    runApplication<SellerApiApplication>(*args)
}
