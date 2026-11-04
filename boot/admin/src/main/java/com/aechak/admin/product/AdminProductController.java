package com.aechak.admin.product;

import com.aechak.admin.audit.AdminAudited;
import com.aechak.admin.product.request.ResumeProductSaleRequest;
import com.aechak.admin.product.request.SuspendProductSaleRequest;
import com.aechak.admin.product.response.AdminProductListResponse;
import com.aechak.application.audit.support.AdminAuditContextHolder;
import com.aechak.application.product.product.admin.usecase.AdminProductUseCase;
import com.aechak.application.product.product.admin.usecase.command.ResumeProductSaleCommand;
import com.aechak.application.product.product.admin.usecase.command.SuspendProductSaleCommand;
import com.aechak.application.product.product.admin.usecase.query.AdminProductSearchQuery;
import com.aechak.domain.audit.auditlog.enums.AdminAuditAction;
import com.aechak.webcommon.response.ApiResponse;
import com.aechak.websecurity.authentication.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 상품 카탈로그 관리 API — 자격(role=ADMIN)은 모듈 게이트(SecurityConfig)가 보증한다.
 * productId는 상품 공개 식별자(ULID)다.
 *
 * <p>검수 큐(/admin/product-inspections)와 가른다. 큐는 판정 대기열이고 여기는 전수 관리다 —
 * 큐에 없는 상품도 보고, 검수와 무관한 이유로 판매를 막는다. 화면의 "검수 통과"와 "블라인드 유지"는
 * 새 경로가 아니라 큐의 승인·반려가 그대로 수행한다(상품의 검수 상태가 구매자 노출을 켜고 끈다).
 */
@RestController
@RequestMapping("/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final AdminProductUseCase adminProductUseCase;

    /**
     * 상품 목록 — 상품 식별자 내림차순(등록 역순). 구매자 노출 조건을 걸지 않아 검수 반려·판매중지
     * 상품도 함께 나온다. inspectionStatus·saleStatus·page·size 형식 오류는 조회 조건이 보정한다.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<AdminProductListResponse>> list(
            @RequestParam(required = false) String inspectionStatus,
            @RequestParam(required = false) String saleStatus,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        AdminProductSearchQuery query =
                AdminProductSearchQuery.of(inspectionStatus, saleStatus, categoryId, keyword, page, size);
        return ResponseEntity.ok(
                ApiResponse.Companion.of(AdminProductListResponse.from(adminProductUseCase.list(query))));
    }

    /**
     * 판매중지 — 판매 상태를 SUSPENDED로 옮기고 막음 표시를 남긴다. 그 표시가 있는 동안 셀러의
     * 판매 상태 변경이 40010으로 거부된다. 이미 막혀 있으면 409(40008), 동시 조치 충돌은 409(40011).
     */
    @PostMapping("/{productId}/sale-suspensions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @AdminAudited(value = AdminAuditAction.PRODUCT_SALE_SUSPENDED, targetVar = "productId")
    public ResponseEntity<Void> suspendSale(
            @PathVariable String productId,
            @Valid @RequestBody SuspendProductSaleRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        // 사유는 요청 본문에만 있다. 조치보다 먼저 올려 둬야 거부된 시도의 행에도 사유가 남는다.
        AdminAuditContextHolder.INSTANCE.reason(request.reason().strip());
        adminProductUseCase.suspendSale(
                new SuspendProductSaleCommand(principal.getUserId(), productId, request.reason()));
        return ResponseEntity.noContent().build();
    }

    /**
     * 판매 재개 — 막음 표시를 지우고 막기 직전 상태로 되돌린다. 막혀 있지 않으면 409(40009).
     * 본문(사유)이 프록시에 잘려 감사 기록이 비는 일을 막으려 DELETE 대신 POST로 받는다(셀러 해제와 같은 결).
     */
    @PostMapping("/{productId}/sale-suspensions/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @AdminAudited(value = AdminAuditAction.PRODUCT_SALE_RESUMED, targetVar = "productId")
    public ResponseEntity<Void> resumeSale(
            @PathVariable String productId,
            @Valid @RequestBody(required = false) ResumeProductSaleRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        AdminAuditContextHolder.INSTANCE.reason(strippedOrNull(request == null ? null : request.reason()));
        adminProductUseCase.resumeSale(new ResumeProductSaleCommand(principal.getUserId(), productId));
        return ResponseEntity.noContent().build();
    }

    /** 공백 한 칸이 "사유를 적었다"로 보이지 않게 한다. */
    private static String strippedOrNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.strip();
    }
}
