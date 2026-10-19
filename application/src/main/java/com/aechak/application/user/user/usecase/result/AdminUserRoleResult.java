package com.aechak.application.user.user.usecase.result;

import com.aechak.domain.user.user.User;
import com.aechak.domain.user.user.enums.UserRole;
import com.aechak.domain.user.user.enums.UserStatus;
import java.time.LocalDateTime;

/** nickname은 온보딩 전 계정이면 null이다. */
public record AdminUserRoleResult(
        long userId,
        String nickname,
        UserRole role,
        UserStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static AdminUserRoleResult from(User user) {
        return new AdminUserRoleResult(
                user.getId(),
                user.getNickname(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
