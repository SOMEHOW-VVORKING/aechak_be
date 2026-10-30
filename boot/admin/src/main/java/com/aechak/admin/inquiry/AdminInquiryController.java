package com.aechak.admin.inquiry;

import com.aechak.admin.audit.AdminAuditExempt;
import com.aechak.admin.audit.AdminAudited;
import com.aechak.admin.inquiry.request.AnswerInquiryRequest;
import com.aechak.admin.inquiry.request.ChangeInquiryStatusRequest;
import com.aechak.admin.inquiry.response.AdminInquiryDetailResponse;
import com.aechak.admin.inquiry.response.AdminInquiryListResponse;
import com.aechak.application.inquiry.usecase.AdminInquiryUseCase;
import com.aechak.application.inquiry.usecase.query.AdminInquirySearchQuery;
import com.aechak.domain.audit.auditlog.enums.AdminAuditAction;
import com.aechak.domain.inquiry.inquiry.enums.InquiryStatus;
import com.aechak.domain.inquiry.inquiry.enums.InquiryType;
import com.aechak.webcommon.response.ApiResponse;
import com.aechak.websecurity.authentication.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 서비스 문의 처리 API — 자격(role=ADMIN)은 모듈 게이트(SecurityConfig)가 보증한다. */
@RestController
@RequestMapping("/admin/inquiries")
@RequiredArgsConstructor
public class AdminInquiryController {

    private final AdminInquiryUseCase adminInquiryUseCase;

    /**
     * 문의 목록 — 상태·유형 필터, 키워드 검색, 커서 페이징. 접수 오래된 순이라 묵은 문의가 먼저 나온다.
     * size 형식 오류는 AdminInquirySearchQuery.of가 보정한다.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<AdminInquiryListResponse>> list(
            @RequestParam(required = false) InquiryStatus status,
            @RequestParam(required = false) InquiryType inquiryType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.Companion.of(AdminInquiryListResponse.from(
                adminInquiryUseCase.list(AdminInquirySearchQuery.of(status, inquiryType, keyword, cursor, size)))));
    }

    /** 문의 상세 — 전문·작성자 표기·답변 기록·같은 작성자의 과거 문의. */
    @GetMapping("/{inquiryId}")
    public ResponseEntity<ApiResponse<AdminInquiryDetailResponse>> detail(@PathVariable long inquiryId) {
        return ResponseEntity.ok(
                ApiResponse.Companion.of(AdminInquiryDetailResponse.from(adminInquiryUseCase.detail(inquiryId))));
    }

    /** 답변 등록 — 저장과 완료 전이가 한 트랜잭션. 처리 직후 화면이 쓸 상세를 그대로 돌려준다. */
    @PostMapping("/{inquiryId}/answer")
    @AdminAudited(value = AdminAuditAction.INQUIRY_ANSWERED, targetVar = "inquiryId")
    public ResponseEntity<ApiResponse<AdminInquiryDetailResponse>> answer(
            @PathVariable long inquiryId,
            @Valid @RequestBody AnswerInquiryRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.Companion.of(AdminInquiryDetailResponse.from(
                adminInquiryUseCase.answer(request.toCommand(principal.getUserId(), inquiryId)))));
    }

    /** 상태 전이 — 처리중·보류. 완료는 답변 등록만의 경로다. */
    @PatchMapping("/{inquiryId}/status")
    @AdminAuditExempt(
            reason = "처리중·보류 전이는 처리 흐름 표시다. 되돌릴 수 없는 조치인 답변 등록(완료 전이)은 기록하고,"
                    + " 이 전이가 남기는 것(현재 상태·담당 운영자)은 문의 행이 그대로 들고 있다.")
    public ResponseEntity<ApiResponse<AdminInquiryDetailResponse>> changeStatus(
            @PathVariable long inquiryId,
            @Valid @RequestBody ChangeInquiryStatusRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.Companion.of(AdminInquiryDetailResponse.from(
                adminInquiryUseCase.changeStatus(request.toCommand(principal.getUserId(), inquiryId)))));
    }
}
