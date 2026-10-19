package com.aechak.application.user.user.service;

import com.aechak.application.support.PageQuery;
import com.aechak.common.error.BusinessException;
import com.aechak.domain.user.error.UserErrorCode;
import com.aechak.domain.user.user.User;
import com.aechak.domain.user.user.enums.UserRole;
import com.aechak.domain.user.user.enums.UserStatus;
import com.aechak.domain.user.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 어드민 관리자 역할 비즈니스 로직 보관함 — Facade에서만 호출된다. 전역 자원 접근이라 소유권 검증이 없다. */
@Service
@RequiredArgsConstructor
public class AdminUserRoleService {

    private final UserRepository userRepository;

    public List<User> findAdminPage(PageQuery pageQuery) {
        return userRepository.findAllByRole(UserRole.ADMIN, pageQuery.getPage(), pageQuery.getSize());
    }

    public long countAdmins() {
        return userRepository.countByRole(UserRole.ADMIN);
    }

    /** 운영자 요청 승격. */
    public void promote(long adminId, long targetUserId) {
        rejectSelfRoleChange(adminId, targetUserId);
        promoteWithoutActor(targetUserId);
    }

    /** 기동 부트스트랩 승격 — 요청자가 없는 경로라 자기 역할 변경 판정이 없다. */
    public void promoteWithoutActor(long userId) {
        User user = getById(userId);
        rejectInactivePromotion(user);
        user.promoteToAdmin();
    }

    /** 운영자 요청 강등 — 관리자가 0명이 되는 강등을 막는다. */
    public void demote(long adminId, long targetUserId) {
        rejectSelfRoleChange(adminId, targetUserId);
        List<Long> lockedAdminIds = userRepository.lockIdsByRole(UserRole.ADMIN);
        User target = getById(targetUserId);
        if (target.getRole() == UserRole.ADMIN && lockedAdminIds.size() <= 1) {
            throw new BusinessException(UserErrorCode.LAST_ADMIN_CANNOT_BE_DEMOTED, null, null);
        }
        target.demoteToGeneral();
    }

    private void rejectSelfRoleChange(long adminId, long targetUserId) {
        if (adminId == targetUserId) {
            throw new BusinessException(UserErrorCode.SELF_ROLE_CHANGE_NOT_ALLOWED, null, null);
        }
    }

    /** 이미 관리자인 계정을 제외하고, ACTIVE가 아닌 계정의 승격을 30017로 거부한다. */
    private void rejectInactivePromotion(User user) {
        if (user.getRole() == UserRole.ADMIN) {
            return;
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(UserErrorCode.USER_NOT_PROMOTABLE, null, null);
        }
    }

    private User getById(long userId) {
        User user = userRepository.findById(userId);
        if (user == null) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND, null, null);
        }
        return user;
    }
}
