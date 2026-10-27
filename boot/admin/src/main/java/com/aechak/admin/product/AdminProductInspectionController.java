package com.aechak.admin.product;

import com.aechak.admin.audit.AdminAudited;
import com.aechak.admin.product.request.RejectProductInspectionRequest;
import com.aechak.admin.product.response.AdminProductInspectionDetailResponse;
import com.aechak.admin.product.response.AdminProductInspectionListResponse;
import com.aechak.application.audit.support.AdminAuditContextHolder;
import com.aechak.application.inspection.usecase.AdminProductInspectionUseCase;
import com.aechak.application.support.PageQuery;
import com.aechak.domain.audit.auditlog.enums.AdminAuditAction;
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

    /**
     * appealed=true면 셀러가 이의를 접수한 행만 본다.
     *
     * <p>이의 필터에 status를 주지 않으면 열린 행(PENDING_REVIEW)으로 좁힌다 — 그것이 관리자의
     * 작업 목록이다. 이미 결정이 끝난 이의 이력을 보려면 status를 명시한다. 이의 목록의 정렬은
     * 접수 시각 내림차순이다(전체 목록은 판정 시각 내림차순).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<AdminProductInspectionListResponse>> list(
            @RequestParam(required = false) ReviewQueueStatus status,
            @RequestParam(defaultValue = "false") boolean appealed,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.Companion.of(AdminProductInspectionListResponse.from(
                adminProductInspectionUseCase.list(status, appealed, PageQuery.of(page, size)))));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<AdminProductInspectionDetailResponse>> detail(@PathVariable String productId) {
        return ResponseEntity.ok(ApiResponse.Companion.of(
                AdminProductInspectionDetailResponse.from(adminProductInspectionUseCase.detail(productId))));
    }

    /** 열린 검토 행이 없으면 409(40500). 셀러가 고친 내용이 먼저 AI를 통과한 경우임 */
    @PostMapping("/{productId}/approve")
    @AdminAudited(value = AdminAuditAction.PRODUCT_INSPECTION_APPROVED, targetVar = "productId")
    public ResponseEntity<Void> approve(
            @PathVariable String productId, @AuthenticationPrincipal AuthPrincipal principal) {
        adminProductInspectionUseCase.approve(principal.getUserId(), productId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{productId}/reject")
    @AdminAudited(value = AdminAuditAction.PRODUCT_INSPECTION_REJECTED, targetVar = "productId")
    public ResponseEntity<Void> reject(
            @PathVariable String productId,
            @Valid @RequestBody RejectProductInspectionRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        String reason = request.reason().strip();
        // 사유는 요청 본문에만 있다. 조치보다 먼저 올려 둬야 거부된 시도의 행에도 사유가 남는다.
        AdminAuditContextHolder.INSTANCE.reason(reason);
        adminProductInspectionUseCase.reject(principal.getUserId(), productId, reason);
        return ResponseEntity.noContent().build();
    }
}
