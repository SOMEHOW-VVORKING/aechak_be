package com.aechak.admin.seller;

import com.aechak.admin.seller.response.AdminApplicationDetailResponse;
import com.aechak.admin.seller.response.AdminApplicationListResponse;
import com.aechak.application.seller.usecase.AdminSellerReviewUseCase;
import com.aechak.application.support.PageQuery;
import com.aechak.domain.seller.application.enums.ApplicationStatus;
import com.aechak.webcommon.response.ApiResponse;
import com.aechak.websecurity.authentication.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 셀러 입점 심사 API — 자격(role=ADMIN)은 모듈 게이트(SecurityConfig)가 보증한다. */
@RestController
@RequestMapping("/admin/seller-applications")
@RequiredArgsConstructor
public class AdminSellerApplicationController {

    private final AdminSellerReviewUseCase adminSellerReviewUseCase;

    /** 신청 목록 — status 필터·제출일 내림차순. page/size 형식 오류는 PageQuery.of가 보정한다. */
    @GetMapping
    public ResponseEntity<ApiResponse<AdminApplicationListResponse>> list(
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.Companion.of(
                AdminApplicationListResponse.from(adminSellerReviewUseCase.list(status, PageQuery.of(page, size)))));
    }

    /** 신청 상세 — 계좌 전체 표시·서류 다운로드 URL(단기)·심사 이력·동일 사업자번호 이력. */
    @GetMapping("/{applicationId}")
    public ResponseEntity<ApiResponse<AdminApplicationDetailResponse>> detail(
            @PathVariable long applicationId,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.Companion.of(
                AdminApplicationDetailResponse.from(
                        adminSellerReviewUseCase.detail(principal.getUserId(), applicationId))));
    }
}
