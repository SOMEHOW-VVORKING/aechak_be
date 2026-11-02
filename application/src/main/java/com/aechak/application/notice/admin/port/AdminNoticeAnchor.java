package com.aechak.application.notice.admin.port;

import java.time.LocalDateTime;

/** 커서가 가리키는 마지막 행. created_at만으로는 동시각 행이 갈리지 않아 id를 함께 든다. */
public record AdminNoticeAnchor(LocalDateTime lastCreatedAt, long lastNoticeId) {}
