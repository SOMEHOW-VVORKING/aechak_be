package com.aechak.admin.seller.response;

import com.aechak.application.seller.usecase.result.AdminApplicationSummaryResult;
import com.aechak.domain.seller.application.enums.ApplicationStatus;
import com.aechak.domain.seller.application.enums.BusinessType;
import java.time.LocalDateTime;

public record AdminApplicationSummaryResponse(
        long applicationId,
        ApplicationStatus status,
        BusinessType businessType,
        String businessName,
        String representativeName,
        LocalDateTime submittedAt) {

    public static AdminApplicationSummaryResponse from(AdminApplicationSummaryResult result) {
        return new AdminApplicationSummaryResponse(
                result.applicationId(),
                result.status(),
                result.businessType(),
                result.businessName(),
                result.representativeName(),
                result.submittedAt());
    }
}
