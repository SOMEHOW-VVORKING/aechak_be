package com.aechak.admin.user;

import com.aechak.admin.audit.AdminAudited;
import com.aechak.admin.user.request.PromoteAdminRequest;
import com.aechak.admin.user.response.AdminListResponse;
import com.aechak.application.audit.support.AdminAuditContextHolder;
import com.aechak.application.support.PageQuery;
import com.aechak.application.user.user.usecase.AdminUserRoleUseCase;
import com.aechak.domain.audit.auditlog.enums.AdminAuditAction;
import com.aechak.domain.audit.auditlog.enums.AdminAuditTargetType;
import com.aechak.webcommon.response.ApiResponse;
import com.aechak.websecurity.authentication.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 명부 API — 자격(role=ADMIN)은 모듈 게이트(SecurityConfig)가 보증한다. */
@RestController
@RequestMapping("/admin/admins")
@RequiredArgsConstructor
public class AdminRosterController {

    private final AdminUserRoleUseCase adminUserRoleUseCase;

    /** 관리자 목록 — 가입 순. page/size 형식 오류는 PageQuery.of가 보정한다. */
    @GetMapping
    public ResponseEntity<ApiResponse<AdminListResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.Companion.of(AdminListResponse.from(
                adminUserRoleUseCase.listAdmins(PageQuery.of(page, size)), principal.getUserId())));
    }

    /** 승격 — 승격된 계정은 api에서 다시 로그인해야 ADMIN 토큰을 받는다. */
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @AdminAudited(AdminAuditAction.ADMIN_PROMOTED)
    public ResponseEntity<Void> promote(
            @Valid @RequestBody PromoteAdminRequest request, @AuthenticationPrincipal AuthPrincipal principal) {
        // 대상이 경로가 아니라 본문에 있어 targetVar로는 잡히지 않는다. 조치보다 먼저 올려야
        // 없는 계정·자기 자신 승격처럼 거부된 시도의 행에도 대상이 남는다.
        AdminAuditContextHolder.INSTANCE.target(AdminAuditTargetType.ADMIN_USER, String.valueOf(request.userId()));
        adminUserRoleUseCase.promote(principal.getUserId(), request.userId());
        return ResponseEntity.noContent().build();
    }

    /** 강등 — 이미 발급된 토큰은 만료까지 ADMIN으로 통과한다. */
    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @AdminAudited(value = AdminAuditAction.ADMIN_DEMOTED, targetVar = "userId")
    public ResponseEntity<Void> demote(@PathVariable long userId, @AuthenticationPrincipal AuthPrincipal principal) {
        adminUserRoleUseCase.demote(principal.getUserId(), userId);
        return ResponseEntity.noContent().build();
    }
}
