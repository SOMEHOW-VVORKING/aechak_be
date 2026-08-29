package com.aechak.seller

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * 셀러센터 실행 모듈 진입점. api와 달리 선별 스캔이다 —
 * 루트(com.aechak) 스캔은 auth, kafka 등 api 전용 조립까지 요구해 부팅이 깨진다.
 * application 패키지를 추가로 쓰게 되면 그 패키지가 요구하는 포트 어댑터(infra)와 조립(config)도 함께 늘린다.
 */
@SpringBootApplication(
    scanBasePackages = [
        "com.aechak.seller",             // 이 모듈 — 컨트롤러와 조립
        "com.aechak.webcommon",          // 전역 예외 핸들러와 응답 규격
        "com.aechak.websecurity",        // JWT 디코더와 상태 필터 부품
        "com.aechak.pii",                // PII 암호화 엔진과 키 조립(PiiCryptoConfig)
        "com.aechak.application.seller", // 입점 신청 유스케이스
        "com.aechak.application.user.user",         // 휴대폰 인증 게이트(requirePhoneVerified)가 UserUseCase 경유
        "com.aechak.application.user.term",         // UserFacade의 온보딩 동의 검증
        "com.aechak.application.user.verification", // 전화 인증
        "com.aechak.application.file",   // 서류 승격
        "com.aechak.application.product", // 상품 등록·카테고리 조회
        "com.aechak.infra.persistence",  // JPA 어댑터 (QuerydslConfig 포함)
        "com.aechak.infra.s3",           // FileStorage 어댑터
        "com.aechak.infra.redis",        // 인증 코드 저장소
        "com.aechak.infra.client.sms",   // SmsSender 어댑터
    ],
)
class SellerApiApplication

fun main(args: Array<String>) {
    runApplication<SellerApiApplication>(*args)
}
