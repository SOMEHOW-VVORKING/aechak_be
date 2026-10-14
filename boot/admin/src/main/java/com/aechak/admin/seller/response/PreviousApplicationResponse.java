package com.aechak.admin.seller.response;

import com.aechak.application.seller.usecase.result.PreviousApplicationResult;
import com.aechak.domain.seller.application.enums.ApplicationStatus;
import java.time.LocalDateTime;

public record PreviousApplicationResponse(long applicationId, ApplicationStatus status, LocalDateTime decidedAt) {

    public static PreviousApplicationResponse from(PreviousApplicationResult result) {
        return new PreviousApplicationResponse(result.applicationId(), result.status(), result.decidedAt());
    }
}
