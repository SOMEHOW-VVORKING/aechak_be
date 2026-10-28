package com.aechak.admin.inspection;

import com.aechak.admin.inspection.response.AdminInspectionMetricsResponse;
import com.aechak.application.inspection.usecase.InspectionMetricsUseCase;
import com.aechak.application.inspection.usecase.query.InspectionMetricsQuery;
import com.aechak.domain.inspection.enums.InspectionTargetType;
import com.aechak.webcommon.response.ApiResponse;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 검수 운영 지표 — 자격(role=ADMIN)은 모듈 게이트(SecurityConfig)가 보증한다.
 *
 * <p>큐 컨트롤러({@code /admin/product-inspections})와 경로를 나눈다. 거기에 두면 큐 상세의
 * {@code /{productId}}(상품 공개 ULID)와 같은 자리를 다투고, 집계는 큐의 한 건이 아니라 다른
 * 자원이다.
 *
 * <p>창은 {@code content_inspections.settled_at} 기준이고 {@code from} 포함·{@code to} 배타다.
 * 창 폭의 상한과 기본 창은 설정이 정한다 — 컨트롤러에서 채우면 주기 로그와 화면이 다른 기본값을
 * 쓰게 되고, 같은 수치를 본다는 전제가 깨진다.
 */
@RestController
@RequestMapping("/admin/inspection-metrics")
@RequiredArgsConstructor
public class AdminInspectionMetricsController {

    private final InspectionMetricsUseCase inspectionMetricsUseCase;

    /** 스냅샷 — 퍼널·판정·누락·비용·적체. 질의 파라미터는 전부 선택이고 창 폭이 상한을 넘으면 40506이다 */
    @GetMapping
    public ResponseEntity<ApiResponse<AdminInspectionMetricsResponse>> metrics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String policyVersion,
            @RequestParam(required = false) InspectionTargetType targetType) {
        InspectionMetricsQuery query = new InspectionMetricsQuery(from, to, policyVersion, targetType);
        return ResponseEntity.ok(ApiResponse.Companion.of(
                AdminInspectionMetricsResponse.from(inspectionMetricsUseCase.snapshot(query))));
    }
}
