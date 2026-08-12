package com.aechak.admin.seller;

import com.aechak.admin.seller.request.RejectApplicationRequest;
import com.aechak.admin.seller.response.AdminApplicationDetailResponse;
import com.aechak.admin.seller.response.AdminApplicationListResponse;
import com.aechak.application.seller.usecase.AdminSellerReviewUseCase;
import com.aechak.application.support.PageQuery;
import com.aechak.domain.seller.application.enums.ApplicationStatus;
import com.aechak.webcommon.response.ApiResponse;
import com.aechak.websecurity.authentication.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    /** 승인 — 한 트랜잭션으로 셀러 개점 + 정산계좌 이관(VERIFIED). 처리 불가 상태·동시 충돌은 409(10101). */
    @PostMapping("/{applicationId}/approve")
    public ResponseEntity<Void> approve(
            @PathVariable long applicationId,
            @AuthenticationPrincipal AuthPrincipal principal) {
        adminSellerReviewUseCase.approve(principal.getUserId(), applicationId);
        return ResponseEntity.noContent().build();
    }

    /** 반려 — 사유 필수(10102). 신청자는 수정 후 재제출한다. */
    @PostMapping("/{applicationId}/reject")
    public ResponseEntity<Void> reject(
            @PathVariable long applicationId,
            @Valid @RequestBody RejectApplicationRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        adminSellerReviewUseCase.reject(
                principal.getUserId(), applicationId, request.reason() == null ? "" : request.reason());
        return ResponseEntity.noContent().build();
    }
}
