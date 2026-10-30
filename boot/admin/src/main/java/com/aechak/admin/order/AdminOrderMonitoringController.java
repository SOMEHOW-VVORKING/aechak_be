package com.aechak.admin.order;

import com.aechak.admin.audit.AdminAudited;
import com.aechak.admin.order.request.AdminOrderMetricsRequest;
import com.aechak.admin.order.request.AdminOrderSearchRequest;
import com.aechak.admin.order.request.NudgeOrderDispatchRequest;
import com.aechak.admin.order.response.AdminOrderDetailResponse;
import com.aechak.admin.order.response.AdminOrderDispatchNudgeResponse;
import com.aechak.admin.order.response.AdminOrderListResponse;
import com.aechak.admin.order.response.AdminOrderMetricsResponse;
import com.aechak.application.audit.support.AdminAuditContextHolder;
import com.aechak.application.order.admin.usecase.AdminOrderMonitoringUseCase;
import com.aechak.domain.audit.auditlog.enums.AdminAuditAction;
import com.aechak.webcommon.response.ApiResponse;
import com.aechak.websecurity.authentication.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 주문 배송 모니터링 API — 자격(role=ADMIN)은 모듈 게이트(SecurityConfig)가 보증한다.
 * orderId는 주문 공개 식별자(ULID)다.
 *
 * <p>지표를 목록과 경로로 나눈다. 목록 응답에 매 페이지 집계를 실으면 커서를 넘길 때마다 전체 범위를
 * 다시 세고, 카드가 바라보는 모수도 목록 필터와 다르다(상태로 좁히면 카드 넷 중 셋이 0이 된다).
 */
@RestController
@RequestMapping("/admin/orders")
@RequiredArgsConstructor
public class AdminOrderMonitoringController {

    private final AdminOrderMonitoringUseCase adminOrderMonitoringUseCase;

    /** 전체 주문 목록 — 상태·셀러·기간·지연 필터와 키워드 검색, 커서 페이지네이션. 결제대기 주문은 대상이 아니다 */
    @GetMapping
    public ResponseEntity<ApiResponse<AdminOrderListResponse>> list(
            @Valid @ModelAttribute AdminOrderSearchRequest request) {
        return ResponseEntity.ok(ApiResponse.Companion.of(
                AdminOrderListResponse.from(adminOrderMonitoringUseCase.getOrders(request.toQuery()))));
    }

    /** 모니터링 카드 — 발송 대기·배송 중·지연·조회 범위 결제액 */
    @GetMapping("/metrics")
    public ResponseEntity<ApiResponse<AdminOrderMetricsResponse>> metrics(
            @Valid @ModelAttribute AdminOrderMetricsRequest request) {
        return ResponseEntity.ok(ApiResponse.Companion.of(
                AdminOrderMetricsResponse.from(adminOrderMonitoringUseCase.getOrderMetrics(request.toQuery()))));
    }

    /** 주문 상세 — 품목 전문·상태 전이 이력·독촉 이력 */
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<AdminOrderDetailResponse>> detail(@PathVariable String orderId) {
        return ResponseEntity.ok(ApiResponse.Companion.of(
                AdminOrderDetailResponse.from(adminOrderMonitoringUseCase.getOrderDetail(orderId))));
    }

    /**
     * 셀러 발송 독촉 기록. 발송 전 상태가 아니면 409(50500), 쿨다운 안의 재독촉이면 409(50501)다.
     *
     * <p>알림은 나가지 않는다 — 어드민 실행 모듈이 발송 어댑터를 조립하지 않는다. 응답은 남은 기록이다.
     */
    @PostMapping("/{orderId}/dispatch-nudges")
    @AdminAudited(value = AdminAuditAction.ORDER_DISPATCH_NUDGED, targetVar = "orderId")
    public ResponseEntity<ApiResponse<AdminOrderDispatchNudgeResponse>> nudgeDispatch(
            @PathVariable String orderId,
            @Valid @RequestBody NudgeOrderDispatchRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        // 메모는 요청 본문에만 있다. 조치보다 먼저 올려 둬야 쿨다운에 막힌 시도의 행에도 남는다.
        AdminAuditContextHolder.INSTANCE.reason(
                request.memo() == null || request.memo().isBlank()
                        ? null
                        : request.memo().strip());
        AdminOrderDispatchNudgeResponse response = AdminOrderDispatchNudgeResponse.from(
                adminOrderMonitoringUseCase.nudgeDispatch(request.toCommand(principal.getUserId(), orderId)));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.Companion.of(response));
    }
}
