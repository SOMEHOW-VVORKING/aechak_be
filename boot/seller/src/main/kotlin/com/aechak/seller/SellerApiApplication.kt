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
        "com.aechak.application.order.seller", // 셀러 주문 관리 — 구매자 주문서·장바구니 조립은 끌고 오지 않는다
        "com.aechak.application.order.claim", // 클레임 승인·거부 — 구매자 요청 계약과 파사드를 공유한다. 환불 집행은 아래 제외 필터로 뺀다
        "com.aechak.application.order.delivery", // 배송완료 전이 — 셀러 수동 처리와 배치 자동 전이가 공유하는 묶음
        "com.aechak.application.order.shipment", // 택배사 마스터 조회 — order BC 중 이 하위만 쓴다
        "com.aechak.infra.persistence", // JPA 어댑터 (QuerydslConfig 포함)
        "com.aechak.infra.s3", // FileStorage 어댑터
        "com.aechak.infra.redis", // 인증 코드 저장소
        "com.aechak.infra.client.sms", // SmsSender 어댑터
        // 검수 요청 발행. 아웃박스에 적고 커밋 직후 보내는 쪽만 조립한다.
        // consumer·outbox 패키지는 넣지 않는다 — 판정 수신과 재발행 스윕은 api가 소유한다.
        "com.aechak.infra.kafka.publisher",
    ],
    excludeFilters = [
        // @ComponentScan을 직접 선언하면 @SpringBootApplication의 메타 필터가 가려진다 — 기본 두 개를 복원한다.
        // TypeExcludeFilter가 없으면 슬라이스 테스트(@WebMvcTest 등)의 빈 걸러내기가 동작하지 않는다.
        ComponentScan.Filter(type = FilterType.CUSTOM, classes = [TypeExcludeFilter::class]),
        ComponentScan.Filter(type = FilterType.CUSTOM, classes = [AutoConfigurationExcludeFilter::class]),
        // 어드민 전용 구현체 제외. 바이너리 이름에는 중첩 클래스의 '$'가 들어올 수 있어 .*로 받는다.
        ComponentScan.Filter(type = FilterType.REGEX, pattern = ["com\\.aechak\\.application\\..*\\.Admin.*"]),
        // 환불 집행 전용 구현체 제외. 클레임 패키지에 살지만 결제 취소(application.payment)와 PG 어댑터를 요구해
        // 조립하면 셀러센터가 결제 본체까지 끌고 와야 한다. 집행은 배치가 맡으므로 이름 규약으로 가린다.
        ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = ["com\\.aechak\\.application\\.order\\.claim\\..*\\.ClaimRefund.*"],
        ),
        // 자동승인 전용 구현체 제외. 셀러 자격을 대조하지 않고 승인하는 경로라 셀러센터에 두면
        // 컨트롤러가 실수로 그 빈을 집는 순간 자격 검증이 비어 버린다. 승인 주체는 배치뿐이다.
        ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = ["com\\.aechak\\.application\\.order\\.claim\\..*\\.ClaimAutoApprove.*"],
        ),
    ],
)
class SellerApiApplication

fun main(args: Array<String>) {
    runApplication<SellerApiApplication>(*args)
}
