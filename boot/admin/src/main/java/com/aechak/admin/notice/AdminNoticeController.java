package com.aechak.admin.notice;

import com.aechak.admin.notice.request.SaveNoticeRequest;
import com.aechak.admin.notice.response.AdminNoticeDetailResponse;
import com.aechak.admin.notice.response.AdminNoticeListResponse;
import com.aechak.application.notice.admin.usecase.AdminNoticeUseCase;
import com.aechak.application.notice.admin.usecase.command.PublishNoticeCommand;
import com.aechak.application.notice.admin.usecase.command.UnpublishNoticeCommand;
import com.aechak.application.notice.admin.usecase.query.AdminNoticeSearchQuery;
import com.aechak.domain.notice.notice.enums.NoticeAudience;
import com.aechak.domain.notice.notice.enums.NoticeCategory;
import com.aechak.domain.notice.notice.enums.NoticeStatus;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * 서비스 공지 작성·게시 API — 자격(role=ADMIN)은 모듈 게이트(SecurityConfig)가 보증한다.
 *
 * <p>게시와 내림을 수정과 다른 경로로 둔 이유: 화면이 내용을 고친 뒤 게시를 누르는 흐름이라
 * 한 요청에 묶으면 "고치기만 하고 게시는 미루기"가 불가능해진다. 프런트는 PUT → publish 두 번 부른다.
 */
@RestController
@RequestMapping("/admin/notices")
@RequiredArgsConstructor
public class AdminNoticeController {

    private final AdminNoticeUseCase adminNoticeUseCase;

    /**
     * 공지 목록 — 상태·대상·분류 필터, 제목 검색, 커서 페이징. 최근 작성순.
     * size 형식 오류는 AdminNoticeSearchQuery.of가 보정한다.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<AdminNoticeListResponse>> list(
            @RequestParam(required = false) NoticeStatus status,
            @RequestParam(required = false) NoticeAudience audience,
            @RequestParam(required = false) NoticeCategory category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.Companion.of(AdminNoticeListResponse.from(adminNoticeUseCase.list(
                AdminNoticeSearchQuery.of(status, audience, category, keyword, cursor, size)))));
    }

    /** 공지 초안 생성. 게시는 별도 경로다 — 쓰는 즉시 노출되는 길을 두지 않는다. */
    @PostMapping
    public ResponseEntity<ApiResponse<AdminNoticeDetailResponse>> create(
            @Valid @RequestBody SaveNoticeRequest request, @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.Companion.of(AdminNoticeDetailResponse.from(
                        adminNoticeUseCase.create(request.toCreateCommand(principal.getUserId())))));
    }

    /** 공지 상세 — 전문과 내부 메모까지. */
    @GetMapping("/{noticeId}")
    public ResponseEntity<ApiResponse<AdminNoticeDetailResponse>> detail(@PathVariable String noticeId) {
        return ResponseEntity.ok(
                ApiResponse.Companion.of(AdminNoticeDetailResponse.from(adminNoticeUseCase.detail(noticeId))));
    }

    /** 내용 수정 — 게시 중 공지도 고칠 수 있고 게시 상태는 그대로 남는다. */
    @PutMapping("/{noticeId}")
    public ResponseEntity<ApiResponse<AdminNoticeDetailResponse>> update(
            @PathVariable String noticeId,
            @Valid @RequestBody SaveNoticeRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.Companion.of(AdminNoticeDetailResponse.from(
                adminNoticeUseCase.update(request.toUpdateCommand(principal.getUserId(), noticeId)))));
    }

    /** 게시 — 이 시점부터 대상 독자의 목록에 올라간다. 발송은 하지 않는다. */
    @PostMapping("/{noticeId}/publish")
    public ResponseEntity<ApiResponse<AdminNoticeDetailResponse>> publish(
            @PathVariable String noticeId, @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.Companion.of(AdminNoticeDetailResponse.from(
                adminNoticeUseCase.publish(new PublishNoticeCommand(principal.getUserId(), noticeId)))));
    }

    /** 내림 — 독자 목록에서 사라진다. 게시 기록(publishedAt)은 남는다. */
    @PostMapping("/{noticeId}/unpublish")
    public ResponseEntity<ApiResponse<AdminNoticeDetailResponse>> unpublish(
            @PathVariable String noticeId, @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.Companion.of(AdminNoticeDetailResponse.from(
                adminNoticeUseCase.unpublish(new UnpublishNoticeCommand(principal.getUserId(), noticeId)))));
    }
}
