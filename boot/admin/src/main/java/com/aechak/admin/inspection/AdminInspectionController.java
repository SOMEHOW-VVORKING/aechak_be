package com.aechak.admin.inspection;

import com.aechak.admin.inspection.response.AdminInspectionDetailResponse;
import com.aechak.admin.inspection.response.AdminInspectionListResponse;
import com.aechak.application.inspection.usecase.AdminInspectionQueryUseCase;
import com.aechak.application.inspection.usecase.query.AdminInspectionSearchQuery;
import com.aechak.domain.inspection.enums.InspectionJudgment;
import com.aechak.domain.inspection.enums.InspectionProcessingState;
import com.aechak.domain.inspection.enums.InspectionTargetType;
import com.aechak.webcommon.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 검수 진행 현황 API — 자격(role=ADMIN)은 모듈 게이트(SecurityConfig)가 보증한다. 조회만 한다.
 *
 * <p><b>같은 BC의 기존 두 경로와 묻는 것이 다르다.</b>
 *
 * <ul>
 *   <li>{@code /admin/inspection-metrics}는 집계다. 창이 {@code settled_at} 기준이라 끝난 건만
 *       센다 — 지금 돌고 있는 건은 어느 수치에도 들어오지 않는다.
 *   <li>{@code /admin/product-inspections}는 관리자 확인이 남은 큐다. AI가 위반으로 가린 상품만
 *       올라오고, 통과한 건과 아직 판정을 기다리는 건은 애초에 등재되지 않는다.
 * </ul>
 *
 * <p>그래서 "방금 등록한 상품의 검수가 지금 어디까지 왔는지"는 둘 중 어느 것으로도 답할 수 없다.
 * 이 경로가 그 질문을 받는다 — 집계가 아니라 검수 건 하나하나이고, 큐와 달리 판정 전·통과까지
 * 전부 본다. 식별자는 검수 행의 id다(검수에는 공개 식별자가 없다).
 *
 * <p>조회라 {@code @AdminAudited}를 붙이지 않는다 — 감사 기록은 운영자의 조치를 남기는 것이고
 * 이 경로는 아무것도 바꾸지 않는다.
 */
@RestController
@RequestMapping("/admin/inspections")
@RequiredArgsConstructor
public class AdminInspectionController {

    private final AdminInspectionQueryUseCase adminInspectionQueryUseCase;

    /**
     * 목록 — 요청 시각 내림차순 고정. 방금 등록한 건이 맨 위에 와야 하는 화면이라 정렬을 받지 않는다.
     *
     * <p>세 필터는 모두 선택이고 주지 않으면 전체다. 알 수 없는 열거 값은 400(90001)이고,
     * page·size의 형식 오류는 조회 조건이 보정한다(음수 page는 0, size는 1~100으로 절삭).
     *
     * <p><b>판정이 아직 없는 건은 {@code judgment}로 고를 수 없다.</b> 그 상태는 판정 칼럼이 빈
     * 것이고 판정 열거에는 그 값이 없다 — {@code processingState=QUEUED} 또는 {@code IN_FLIGHT}로
     * 고른다. 판정에 "없음"을 만들어 두 축을 합치면 기한 초과(UNJUDGED)와 아직 안 끝난 건이 같은
     * 칸에 섞인다.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<AdminInspectionListResponse>> list(
            @RequestParam(required = false) InspectionProcessingState processingState,
            @RequestParam(required = false) InspectionTargetType targetType,
            @RequestParam(required = false) InspectionJudgment judgment,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        AdminInspectionSearchQuery query =
                AdminInspectionSearchQuery.of(processingState, targetType, judgment, page, size);
        return ResponseEntity.ok(
                ApiResponse.Companion.of(AdminInspectionListResponse.from(adminInspectionQueryUseCase.list(query))));
    }

    /** 상세 — 목록 행에 관점별 결과·사유·버전·임차를 더한다. 없는 검수는 404(40508) */
    @GetMapping("/{inspectionId}")
    public ResponseEntity<ApiResponse<AdminInspectionDetailResponse>> detail(@PathVariable long inspectionId) {
        return ResponseEntity.ok(ApiResponse.Companion.of(
                AdminInspectionDetailResponse.from(adminInspectionQueryUseCase.detail(inspectionId))));
    }
}
