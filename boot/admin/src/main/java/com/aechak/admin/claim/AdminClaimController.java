package com.aechak.admin.claim;

import com.aechak.admin.claim.request.InterveneClaimRequest;
import com.aechak.admin.claim.response.AdminClaimDetailResponse;
import com.aechak.admin.claim.response.AdminClaimListResponse;
import com.aechak.admin.claim.response.ClaimInterventionReasonCatalogResponse;
import com.aechak.admin.claim.response.InterveneClaimResponse;
import com.aechak.application.order.claim.admin.port.AdminClaimSort;
import com.aechak.application.order.claim.admin.usecase.AdminClaimUseCase;
import com.aechak.application.order.claim.admin.usecase.query.AdminClaimSearchQuery;
import com.aechak.application.support.CursorPageSize;
import com.aechak.domain.order.claim.enums.ClaimStatus;
import com.aechak.domain.order.claim.enums.ClaimType;
import com.aechak.webcommon.response.ApiResponse;
import com.aechak.websecurity.authentication.AuthPrincipal;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 운영자 클레임 조회·개입 API. claimPublicId는 클레임 공개 식별자(ULID)임 */
@RestController
@RequestMapping("/admin/claims")
@RequiredArgsConstructor
public class AdminClaimController {

    private final AdminClaimUseCase adminClaimUseCase;

    /**
     * 기본 정렬은 요청 오래된 순 — 분쟁이 길어진 건이 운영의 작업 목록 위에 와야 한다.
     *
     * <p>openOnly를 주지 않으면 status가 비어 있을 때만 개입 가능한 건으로 좁힌다 — 아무 축도 주지 않은
     * 요청이 받아야 하는 것은 운영자의 작업 목록이고, 상태를 명시한 요청은 종결된 이력을 보려는 것이다.
     * 종결된 건까지 아무 조건 없이 보려면 openOnly=false를 명시한다.
     *
     * <p>기간은 [requestedFrom, requestedTo) 반열림 구간이다. 커서를 주면 커서에 담긴 정렬축이 sort를 덮는다.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<AdminClaimListResponse>> search(
            @RequestParam(required = false) List<ClaimType> claimType,
            @RequestParam(required = false) List<ClaimStatus> status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime requestedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime requestedTo,
            @RequestParam(required = false) String orderPublicId,
            @RequestParam(required = false) String sellerKeyword,
            @RequestParam(required = false) String buyerKeyword,
            @RequestParam(required = false) Boolean openOnly,
            @RequestParam(defaultValue = "OLDEST_FIRST") AdminClaimSort sort,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        AdminClaimSearchQuery query = new AdminClaimSearchQuery(
                claimType == null ? List.of() : claimType,
                status == null ? List.of() : status,
                requestedFrom,
                requestedTo,
                orderPublicId,
                sellerKeyword,
                buyerKeyword,
                openOnly,
                sort,
                cursor,
                pageSize(size));
        return ResponseEntity.ok(
                ApiResponse.Companion.of(AdminClaimListResponse.from(adminClaimUseCase.searchClaims(query))));
    }

    /** 귀책 판정별 개입 사유 선택지. 고정 어휘라 DB를 보지 않는다. */
    @GetMapping("/intervention-reasons")
    public ResponseEntity<ApiResponse<ClaimInterventionReasonCatalogResponse>> interventionReasons() {
        return ResponseEntity.ok(ApiResponse.Companion.of(
                ClaimInterventionReasonCatalogResponse.from(adminClaimUseCase.getInterventionReasons())));
    }

    @GetMapping("/{claimPublicId}")
    public ResponseEntity<ApiResponse<AdminClaimDetailResponse>> detail(@PathVariable String claimPublicId) {
        return ResponseEntity.ok(
                ApiResponse.Companion.of(AdminClaimDetailResponse.from(adminClaimUseCase.getClaim(claimPublicId))));
    }

    /**
     * 셀러 처리를 덮고 분쟁을 종결한다. 개입할 수 없는 상태면 409(50414).
     *
     * <p>환불 집행·결제 취소는 하지 않는다 — 정하는 것은 귀책과 반품비 부담 주체다.
     * 응답의 refundStatus/refundSettled가 환불이 그대로임을 보여 준다.
     */
    @PostMapping("/{claimPublicId}/intervention")
    public ResponseEntity<ApiResponse<InterveneClaimResponse>> intervene(
            @PathVariable String claimPublicId,
            @Valid @RequestBody InterveneClaimRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.Companion.of(InterveneClaimResponse.from(
                adminClaimUseCase.interveneClaim(request.toCommand(principal.getUserId(), claimPublicId)))));
    }

    /** 형식 오류는 보정한다 — 목록 크기는 업무 규칙이 아니라 표시 선택이다. */
    private static int pageSize(Integer size) {
        if (size == null) {
            return CursorPageSize.DEFAULT;
        }
        return (int) Math.min(Math.max(size, CursorPageSize.MIN), CursorPageSize.MAX);
    }
}
