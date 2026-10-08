package com.aechak.application.seller.usecase.result;

import com.aechak.domain.seller.application.SellerApplication;
import com.aechak.domain.seller.application.enums.ApplicationStatus;
import java.time.LocalDateTime;

public record PreviousApplicationResult(long applicationId, ApplicationStatus status, LocalDateTime decidedAt) {

    public static PreviousApplicationResult from(SellerApplication application) {
        return new PreviousApplicationResult(
                application.getId(), application.getStatus(), application.getDecidedAt());
    }
}
