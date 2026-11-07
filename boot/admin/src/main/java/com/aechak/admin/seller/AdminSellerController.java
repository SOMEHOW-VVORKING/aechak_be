package com.aechak.admin.seller;

import com.aechak.admin.audit.AdminAudited;
import com.aechak.admin.seller.request.ReleaseSellerSuspensionRequest;
import com.aechak.admin.seller.request.SaveSellerMemoRequest;
import com.aechak.admin.seller.request.SuspendSellerRequest;
import com.aechak.admin.seller.response.AdminSellerDetailResponse;
import com.aechak.admin.seller.response.AdminSellerListResponse;
import com.aechak.application.audit.support.AdminAuditContextHolder;
import com.aechak.application.seller.usecase.AdminSellerAccountUseCase;
import com.aechak.application.seller.usecase.command.ReleaseSellerSuspensionCommand;
import com.aechak.application.seller.usecase.command.SaveSellerMemoCommand;
import com.aechak.application.seller.usecase.command.SuspendSellerCommand;
import com.aechak.application.seller.usecase.query.AdminSellerSearchQuery;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 입점 셀러 계정 관리 API — 자격(role=ADMIN)은 모듈 게이트(SecurityConfig)가 보증한다.
 *
 * <p>여기서 막는 것은 <b>판매</b>다. 계정 로그인 차단은 /admin/members가 users.status로 따로 다루고,
 * 셀러도 role=GENERAL이라 한 계정이 양쪽에 등장할 수 있다 — 두 층을 섞지 않는다.
 */
@RestController
@RequestMapping("/admin/sellers")
@RequiredArgsConstructor
public class AdminSellerController {

    private final AdminSellerAccountUseCase adminSellerAccountUseCase;

    /**
     * 셀러 목록 — 셀러 식별자 내림차순(개점 역순).
     * status·sellerType·page·size 형식 오류는 AdminSellerSearchQuery.of가 보정한다.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<AdminSellerListResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sellerType,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        AdminSellerSearchQuery query = AdminSellerSearchQuery.of(status, sellerType, keyword, page, size);
        return ResponseEntity.ok(
                ApiResponse.Companion.of(AdminSellerListResponse.from(adminSellerAccountUseCase.list(query))));
    }

    /** 셀러 상세 — 현재 정지 사유·내부 메모·제재 이력. 셀러가 아니면 404(10000). */
    @GetMapping("/{sellerId}")
    public ResponseEntity<ApiResponse<AdminSellerDetailResponse>> detail(@PathVariable long sellerId) {
        return ResponseEntity.ok(
                ApiResponse.Companion.of(AdminSellerDetailResponse.from(adminSellerAccountUseCase.detail(sellerId))));
    }

    /** 판매정지 — 이력 한 행이 함께 남는다. 이미 정지면 409(10007), 탈퇴·동시 충돌은 409(10009). */
    @PostMapping("/{sellerId}/suspensions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @AdminAudited(value = AdminAuditAction.SELLER_SUSPENDED, targetVar = "sellerId")
    public ResponseEntity<Void> suspend(
            @PathVariable long sellerId,
            @Valid @RequestBody SuspendSellerRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        // 사유는 요청 본문에만 있다. 조치보다 먼저 올려 둬야 거부된 시도의 행에도 사유가 남는다.
        AdminAuditContextHolder.INSTANCE.reason(request.reasonCode().describe(request.reasonDetail()));
        adminSellerAccountUseCase.suspend(new SuspendSellerCommand(
                principal.getUserId(),
                sellerId,
                request.reasonCode(),
                request.reasonDetail(),
                request.durationDays(),
                request.memo()));
        return ResponseEntity.noContent().build();
    }

    /**
     * 판매정지 해제 — 정지 직전 상태로 되돌린다. 정지가 아니면 409(10008).
     * 본문(사유·메모)이 프록시에 잘려 감사 기록이 비는 일을 막으려 DELETE 대신 POST로 받는다(회원 해제와 같은 결).
     */
    @PostMapping("/{sellerId}/suspensions/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @AdminAudited(value = AdminAuditAction.SELLER_SUSPENSION_RELEASED, targetVar = "sellerId")
    public ResponseEntity<Void> release(
            @PathVariable long sellerId,
            @Valid @RequestBody(required = false) ReleaseSellerSuspensionRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        ReleaseSellerSuspensionRequest body =
                request == null ? new ReleaseSellerSuspensionRequest(null, null) : request;
        AdminAuditContextHolder.INSTANCE.reason(strippedOrNull(body.reasonDetail()));
        adminSellerAccountUseCase.releaseSuspension(
                new ReleaseSellerSuspensionCommand(principal.getUserId(), sellerId, body.reasonDetail(), body.memo()));
        return ResponseEntity.noContent().build();
    }

    /** 내부 메모 저장 — 덮어쓰기다. 제재 이력은 남지 않고 감사 로그에만 남는다. */
    @PutMapping("/{sellerId}/memo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @AdminAudited(value = AdminAuditAction.SELLER_MEMO_UPDATED, targetVar = "sellerId")
    public ResponseEntity<Void> saveMemo(
            @PathVariable long sellerId,
            @Valid @RequestBody(required = false) SaveSellerMemoRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        String memo = request == null ? null : request.memo();
        adminSellerAccountUseCase.saveMemo(new SaveSellerMemoCommand(principal.getUserId(), sellerId, memo));
        return ResponseEntity.noContent().build();
    }

    /** 메모와 달리 사유는 비어 있으면 적지 않는다 — 공백 한 칸이 "사유를 적었다"로 보이지 않게 한다. */
    private static String strippedOrNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.strip();
    }
}
