package com.aechak.application.seller.usecase.result;

import java.time.LocalDateTime;

/** uploadedAt은 교체 시각까지 반영해야 하므로 updated_at을 쓴다(신청자측과 동일 규칙). */
public record AdminApplicationDocumentResult(String documentType, LocalDateTime uploadedAt, String downloadUrl) {}
