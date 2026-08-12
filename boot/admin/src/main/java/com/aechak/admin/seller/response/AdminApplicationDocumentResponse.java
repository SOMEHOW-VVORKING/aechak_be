package com.aechak.admin.seller.response;

import com.aechak.application.seller.usecase.result.AdminApplicationDocumentResult;
import java.time.LocalDateTime;

public record AdminApplicationDocumentResponse(String documentType, LocalDateTime uploadedAt, String downloadUrl) {

    public static AdminApplicationDocumentResponse from(AdminApplicationDocumentResult result) {
        return new AdminApplicationDocumentResponse(result.documentType(), result.uploadedAt(), result.downloadUrl());
    }
}
