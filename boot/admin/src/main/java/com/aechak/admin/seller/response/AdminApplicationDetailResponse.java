package com.aechak.admin.seller.response;

import com.aechak.application.seller.usecase.result.AdminApplicationDetailResult;
import com.aechak.domain.seller.application.enums.ApplicationStatus;
import com.aechak.domain.seller.application.enums.BusinessType;
import java.time.LocalDateTime;
import java.util.List;

/** 어드민 상세 — 계좌는 전체 표시(통장사본 대조용). 신청자측 accountNumberMasked를 accountNumber가 대체한다(계약). */
public record AdminApplicationDetailResponse(
        long applicationId,
        ApplicationStatus status,
        BusinessType businessType,
        String businessName,
        String businessRegNo,
        String corpRegNo,
        String representativeName,
        String telesalesNumber,
        String bankCode,
        String accountNumber,
        String accountHolder,
        LocalDateTime appliedAt,
        LocalDateTime submittedAt,
        LocalDateTime decidedAt,
        String rejectionReason,
        List<AdminApplicationDocumentResponse> documents,
        List<ApplicationReviewResponse> reviews,
        List<PreviousApplicationResponse> previousApplications) {

    public static AdminApplicationDetailResponse from(AdminApplicationDetailResult result) {
        return new AdminApplicationDetailResponse(
                result.applicationId(),
                result.status(),
                result.businessType(),
                result.businessName(),
                result.businessRegNo(),
                result.corpRegNo(),
                result.representativeName(),
                result.telesalesNumber(),
                result.bankCode(),
                result.accountNumber(),
                result.accountHolder(),
                result.appliedAt(),
                result.submittedAt(),
                result.decidedAt(),
                result.rejectionReason(),
                result.documents().stream().map(AdminApplicationDocumentResponse::from).toList(),
                result.reviews().stream().map(ApplicationReviewResponse::from).toList(),
                result.previousApplications().stream().map(PreviousApplicationResponse::from).toList());
    }
}
