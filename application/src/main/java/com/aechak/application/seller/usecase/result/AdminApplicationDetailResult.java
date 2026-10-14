package com.aechak.application.seller.usecase.result;

import com.aechak.domain.seller.application.ApplicationReview;
import com.aechak.domain.seller.application.SellerApplication;
import com.aechak.domain.seller.application.enums.ApplicationStatus;
import com.aechak.domain.seller.application.enums.BusinessType;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/** 어드민 상세 — 계좌는 전체 표시(통장사본 대조용, 계약 명세). 신청자측 마스킹 모델과 의도적으로 다르다. */
public record AdminApplicationDetailResult(
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
        List<AdminApplicationDocumentResult> documents,
        List<ApplicationReviewResult> reviews,
        List<PreviousApplicationResult> previousApplications) {

    /** accountNumber는 호출부가 복호화해 넘긴 평문 — 어드민 상세에만 존재하는 값이다. */
    public static AdminApplicationDetailResult from(
            SellerApplication application,
            String accountNumber,
            List<AdminApplicationDocumentResult> documents,
            List<PreviousApplicationResult> previousApplications) {
        return new AdminApplicationDetailResult(
                application.getId(),
                application.getStatus(),
                application.getBusinessType(),
                application.getBusinessName(),
                application.getBusinessRegNo(),
                application.getCorpRegNo(),
                application.getRepresentativeName(),
                application.getTelesalesNumber(),
                application.getBankCode(),
                accountNumber,
                application.getAccountHolder(),
                application.getCreatedAt(),
                application.getSubmittedAt(),
                application.getDecidedAt(),
                application.getStatus() == ApplicationStatus.REJECTED ? application.getRejectionReason() : null,
                documents,
                application.getReviews().stream()
                        .sorted(Comparator.comparing(ApplicationReview::getId))
                        .map(ApplicationReviewResult::from)
                        .toList(),
                previousApplications);
    }
}
