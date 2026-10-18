package com.aechak.application.user.user.service;

import com.aechak.application.support.PageQuery;
import com.aechak.common.error.BusinessException;
import com.aechak.domain.user.error.UserErrorCode;
import com.aechak.domain.user.user.User;
import com.aechak.domain.user.user.enums.UserRole;
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
        getById(userId).promoteToAdmin();
    }

    public void demote(long userId) {
        getById(userId).demoteToGeneral();
    }

    private User getById(long userId) {
        User user = userRepository.findById(userId);
        if (user == null) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND, null, null);
        }
        return user;
    }
}
