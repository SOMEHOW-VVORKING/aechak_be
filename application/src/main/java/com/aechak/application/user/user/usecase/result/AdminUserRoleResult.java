package com.aechak.application.user.user.usecase.result;

import com.aechak.domain.user.user.User;
import com.aechak.domain.user.user.enums.UserRole;
import com.aechak.domain.user.user.enums.UserStatus;
import java.time.LocalDateTime;

public record AdminUserRoleResult(
        long userId, UserRole role, UserStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {

    public static AdminUserRoleResult from(User user) {
        return new AdminUserRoleResult(
                user.getId(), user.getRole(), user.getStatus(), user.getCreatedAt(), user.getUpdatedAt());
    }
}
