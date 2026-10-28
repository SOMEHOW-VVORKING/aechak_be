package com.aechak.admin.user.request;

import jakarta.validation.constraints.NotNull;

public record PromoteAdminRequest(@NotNull(message = "승격할 사용자 id는 필수입니다.") Long userId) {}
