package com.aechak.application.seller.usecase;

import com.aechak.application.seller.usecase.result.AdminApplicationDetailResult;
import com.aechak.application.seller.usecase.result.AdminApplicationPageResult;
import com.aechak.domain.seller.application.enums.ApplicationStatus;

/**
 * 어드민 셀러 심사 — 자격(role=ADMIN)은 어드민 모듈 게이트가 보증한다.
 * 전역 자원 접근이라 소유권 검증 계층이 없다(신청자측과 다른 점).
 */
public interface AdminSellerReviewUseCase {

    /** 신청 목록 — status 미지정(null) 시 전체, 제출일 내림차순. */
    AdminApplicationPageResult list(ApplicationStatus status, int page, int size);

    /**
     * 신청 상세 — 계좌 전체 표시(통장사본 대조용)·서류별 단기 다운로드 URL·심사 이력·동일 사업자번호 이력.
     * adminId는 서류 URL 발급 감사 로그용.
     */
    AdminApplicationDetailResult detail(long adminId, long applicationId);
}
