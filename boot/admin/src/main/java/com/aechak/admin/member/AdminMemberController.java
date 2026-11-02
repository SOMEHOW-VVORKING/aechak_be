package com.aechak.admin.member;

import com.aechak.admin.audit.AdminAudited;
import com.aechak.admin.member.request.ReleaseSanctionRequest;
import com.aechak.admin.member.request.SanctionMemberRequest;
import com.aechak.admin.member.response.MemberDetailResponse;
import com.aechak.admin.member.response.MemberListResponse;
import com.aechak.application.audit.support.AdminAuditContextHolder;
import com.aechak.application.support.CursorPageSize;
import com.aechak.application.user.sanction.usecase.AdminMemberUseCase;
import com.aechak.application.user.sanction.usecase.command.ReleaseSanctionCommand;
import com.aechak.application.user.sanction.usecase.command.SanctionMemberCommand;
import com.aechak.application.user.sanction.usecase.query.MemberSearchQuery;
import com.aechak.domain.audit.auditlog.enums.AdminAuditAction;
import com.aechak.domain.user.sanction.enums.SanctionReasonCode;
import com.aechak.webcommon.response.ApiResponse;
import com.aechak.websecurity.authentication.AuthPrincipal;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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

/** 일반 회원 관리 API — 자격(role=ADMIN)은 모듈 게이트(SecurityConfig)가 보증한다. */
@RestController
@RequestMapping("/admin/members")
@RequiredArgsConstructor
public class AdminMemberController {

    private final AdminMemberUseCase adminMemberUseCase;

    /**
     * 회원 목록 — 가입 역순. status·size 형식 오류는 MemberSearchQuery.of가 보정하고,
     * 상태 필터를 바꾼 채 보낸 옛 커서는 400(90002)으로 끊는다.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<MemberListResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate joinedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate joinedTo,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        MemberSearchQuery query = MemberSearchQuery.of(
                status, keyword, joinedFrom, joinedTo, cursor, size == null ? CursorPageSize.DEFAULT : size);
        return ResponseEntity.ok(
                ApiResponse.Companion.of(MemberListResponse.from(adminMemberUseCase.listMembers(query))));
    }

    /** 회원 상세 — 가입일·상태·제재 이력. 관리자 계정은 이 경로에 없다(관리자 명부가 따로 있다). */
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<MemberDetailResponse>> detail(@PathVariable long userId) {
        return ResponseEntity.ok(
                ApiResponse.Companion.of(MemberDetailResponse.from(adminMemberUseCase.getMember(userId))));
    }

    /** 제재 적용 — 이용 제한 / 임시 정지 / 영구 정지 / 탈퇴 처리. 이력 한 행이 함께 남는다. */
    @PostMapping("/{userId}/sanctions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @AdminAudited(value = AdminAuditAction.MEMBER_SANCTIONED, targetVar = "userId")
    public ResponseEntity<Void> sanction(
            @PathVariable long userId,
            @Valid @RequestBody SanctionMemberRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        // 사유는 요청 본문에만 있다. 조치보다 먼저 올려 둬야 거부된 시도의 행에도 사유가 남는다.
        AdminAuditContextHolder.INSTANCE.reason(auditReason(request.reasonCode(), request.reasonDetail()));
        adminMemberUseCase.sanction(new SanctionMemberCommand(
                principal.getUserId(),
                userId,
                request.action(),
                request.reasonCode(),
                request.reasonDetail(),
                request.durationDays(),
                request.memo()));
        return ResponseEntity.noContent().build();
    }

    /**
     * 제재 해제 — 본문(사유·메모)이 프록시에 잘려 감사 기록이 비는 일을 막으려 DELETE 대신 POST로 받는다.
     * 해제도 상태를 되돌리는 UPDATE가 아니라 이력 한 행으로 남는다.
     */
    @PostMapping("/{userId}/sanctions/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @AdminAudited(value = AdminAuditAction.MEMBER_SANCTION_RELEASED, targetVar = "userId")
    public ResponseEntity<Void> release(
            @PathVariable long userId,
            @Valid @RequestBody(required = false) ReleaseSanctionRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        ReleaseSanctionRequest body = request == null ? new ReleaseSanctionRequest(null, null) : request;
        AdminAuditContextHolder.INSTANCE.reason(strippedOrNull(body.reasonDetail()));
        adminMemberUseCase.release(
                new ReleaseSanctionCommand(principal.getUserId(), userId, body.reasonDetail(), body.memo()));
        return ResponseEntity.noContent().build();
    }

    /** 감사 행의 사유 칸 — 고정 어휘를 사람이 읽는 문구로 옮기고 상세가 있으면 이어 붙인다. */
    private static String auditReason(SanctionReasonCode reasonCode, String reasonDetail) {
        String detail = strippedOrNull(reasonDetail);
        return detail == null ? reasonCode.getLabel() : reasonCode.getLabel() + " — " + detail;
    }

    /** 메모와 달리 사유는 비어 있으면 적지 않는다 — 공백 한 칸이 "사유를 적었다"로 보이지 않게 한다. */
    private static String strippedOrNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.strip();
    }
}
