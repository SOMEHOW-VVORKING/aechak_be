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

    public void promote(long userId) {
        User user = getById(userId);
        rejectInactivePromotion(user);
        user.promoteToAdmin();
    }

    public void demote(long userId) {
        getById(userId).demoteToGeneral();
    }

    /**
     * 비활성 계정 승격을 도메인 불변식 위반(500) 대신 30017로 거른다.
     * 이미 관리자인 계정은 도메인이 30012로 거르므로 상태를 보지 않는다.
     */
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
