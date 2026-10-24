package com.aechak.admin.product;

import com.aechak.admin.product.request.RejectProductInspectionRequest;
import com.aechak.admin.product.response.AdminProductInspectionDetailResponse;
import com.aechak.admin.product.response.AdminProductInspectionListResponse;
import com.aechak.application.inspection.usecase.AdminProductInspectionUseCase;
import com.aechak.application.support.PageQuery;
import com.aechak.domain.inspection.enums.ReviewQueueStatus;
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

/** 상품 검수 큐 API. productId는 상품 공개 식별자(ULID)임 */
@RestController
@RequestMapping("/admin/product-inspections")
@RequiredArgsConstructor
public class AdminProductInspectionController {

    private final AdminProductInspectionUseCase adminProductInspectionUseCase;

    @GetMapping
    public ResponseEntity<ApiResponse<AdminProductInspectionListResponse>> list(
            @RequestParam(required = false) ReviewQueueStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.Companion.of(AdminProductInspectionListResponse.from(
                adminProductInspectionUseCase.list(status, PageQuery.of(page, size)))));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<AdminProductInspectionDetailResponse>> detail(@PathVariable String productId) {
        return ResponseEntity.ok(ApiResponse.Companion.of(
                AdminProductInspectionDetailResponse.from(adminProductInspectionUseCase.detail(productId))));
    }

    /** 열린 검토 행이 없으면 409(40500). 셀러가 고친 내용이 먼저 AI를 통과한 경우임 */
    @PostMapping("/{productId}/approve")
    public ResponseEntity<Void> approve(
            @PathVariable String productId, @AuthenticationPrincipal AuthPrincipal principal) {
        adminProductInspectionUseCase.approve(principal.getUserId(), productId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{productId}/reject")
    public ResponseEntity<Void> reject(
            @PathVariable String productId,
            @Valid @RequestBody RejectProductInspectionRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        adminProductInspectionUseCase.reject(
                principal.getUserId(), productId, request.reason().strip());
        return ResponseEntity.noContent().build();
    }
}
