package com.aechak.application.seller.usecase.result;

import com.aechak.domain.seller.application.SellerApplication;
import com.aechak.domain.seller.application.enums.ApplicationStatus;
import com.aechak.domain.seller.application.enums.BusinessType;
import java.time.LocalDateTime;

public record AdminApplicationSummaryResult(
        long applicationId,
        ApplicationStatus status,
        BusinessType businessType,
        String businessName,
        String representativeName,
        LocalDateTime submittedAt) {

    public static AdminApplicationSummaryResult from(SellerApplication application) {
        return new AdminApplicationSummaryResult(
                application.getId(),
                application.getStatus(),
                application.getBusinessType(),
                application.getBusinessName(),
                application.getRepresentativeName(),
                application.getSubmittedAt());
    }
}
