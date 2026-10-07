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
            "com.aechak.application.seller", // 심사 유스케이스 (신청자측 파사드도 딸려온다 — 아래 user·file 스캔 이유)
            "com.aechak.application.file", // 서류 다운로드 URL 발급(FileUseCase)
            "com.aechak.application.user.user", // SellerApplicationFacade의 휴대폰 인증 검사 의존(UserUseCase) — user 전체 스캔은 전화
            // 인증(Redis·SMS 포트) 조립까지 요구해 하위로 좁힌다
            "com.aechak.application.user.term", // UserFacade의 약관 동의 의존(ConsentService)
            "com.aechak.infra.persistence", // 리포지토리·UserStatusReader 어댑터
            "com.aechak.infra.s3", // FileStorage 어댑터
        })
public class AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
