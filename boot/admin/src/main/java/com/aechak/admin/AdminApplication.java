package com.aechak.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 어드민 실행 모듈 진입점 — 운영자(role=ADMIN) 전용 API의 조립 지점.
 *
 * <p>전체(com.aechak) 스캔은 api 전용 조립(웹 로그인 정책·SMS 등)이 없는 포트에서 부팅이 깨지므로
 * 어드민이 실제 소비하는 패키지만 스캔한다(batch 결).
 * application 패키지를 스캔에 추가할 땐 그 빈들이 요구하는 포트 어댑터 모듈도 의존에 함께 추가한다.
 */
@SpringBootApplication(
        scanBasePackages = {
            "com.aechak.admin",
            "com.aechak.webcommon", // 전역 예외 핸들러·응답 봉투
            "com.aechak.websecurity", // RS256 디코더 조립(JwtConfig)·프린시펄 변환
            "com.aechak.pii", // PII 암호화 엔진·키 조립(PiiCryptoConfig) — 계좌번호 복호에 필요
            "com.aechak.application.pii", // 암호문 Base64 왕복 부품(PiiStringCodec) — 계좌번호 복호가 경유
            "com.aechak.application.audit", // 감사 로그 기록·조회 — 어드민 전용 BC라 포트 어댑터는 persistence뿐이다
            "com.aechak.application.seller", // 심사 유스케이스 (신청자측 파사드도 딸려온다 — 아래 user·file 스캔 이유)
            "com.aechak.application.settlement", // 승인 tx의 정산계좌 이관 진입점
            "com.aechak.application.file", // 서류 다운로드 URL 발급(FileUseCase)
            // 문의는 하위 두 패키지만 — inquiry 전체를 스캔하면 접수 통지 리스너까지 딸려와
            // 어드민에 없는 EmailSender 어댑터와 통지 정책 빈을 요구하며 부팅이 깨진다.
            "com.aechak.application.inquiry.facade", // 문의 조회·답변 유스케이스
            "com.aechak.application.inquiry.service", // 위 파사드가 쓰는 로직 보관함
            "com.aechak.application.user.user", // SellerApplicationFacade의 휴대폰 인증 검사 의존(UserUseCase) — user 전체 스캔은 전화
            // 인증(Redis·SMS 포트) 조립까지 요구해 하위로 좁힌다
            "com.aechak.application.user.term", // UserFacade의 약관 동의 의존(ConsentService)
            "com.aechak.application.user.sanction", // 일반 회원 조회·제재 유스케이스
            // 배송 정책 파사드의 택배사 검증 의존(CourierUseCase) — 어드민이 쓰는 기능은 아니지만
            // 그 파사드가 application.seller에 살아 패키지 단위 스캔으로는 가릴 수 없다. order BC 중 이 하위만 쓴다
            "com.aechak.application.order.shipment",
            // 주문 배송 모니터링 — order BC 중 이 하위만 쓴다. BC를 통째로 넣으면 구매자측 주문서·장바구니
            // 파사드와 그 리스너가 딸려와 어드민에 없는 빈(결제·카프카 배선)을 요구한다
            "com.aechak.application.order.admin",
            // 어드민 클레임 조회·개입. claim BC 전체가 아니라 어드민 전용 하위만 넣는다 — 상위를 넣으면
            // 환불 집행·자동승인 파사드가 딸려와 PG·재고·적립금 포트 어댑터를 요구하며 부팅이 깨진다.
            // 환불액 계산기는 같은 패키지의 다른 빈과 떼어 와야 해 ClaimAmountScanConfig가 따로 집어 온다
            "com.aechak.application.order.claim.admin",
            // 상품 검수 큐의 노출 전이(ProductInspectionGate 구현). 검수 패키지 자체는 InspectionScanConfig가 스캔함
            "com.aechak.application.product.product.service",
            // 리뷰 관리 패키지는 AdminReviewScanConfig가 스캔함 — 같은 BC의 구매자측 조립을 빼야 해서 여기 두지 않는다
            "com.aechak.infra.persistence", // 리포지토리·UserStatusReader 어댑터
            "com.aechak.infra.s3", // FileStorage 어댑터
        })
public class AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
