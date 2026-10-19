package com.aechak.admin.user.response;

import com.aechak.application.user.user.usecase.result.AdminUserRoleResult;
import com.aechak.domain.user.user.enums.UserStatus;
import java.time.LocalDateTime;

/** nickname은 온보딩 전 계정이면 null. isSelf는 요청자 자신의 행 표시용. */
public record AdminSummaryResponse(
        long userId, String nickname, UserStatus status, LocalDateTime joinedAt, boolean isSelf) {

    public static AdminSummaryResponse from(AdminUserRoleResult result, long currentAdminId) {
        return new AdminSummaryResponse(
                result.userId(),
                result.nickname(),
                result.status(),
                result.createdAt(),
                result.userId() == currentAdminId);
    }
}
