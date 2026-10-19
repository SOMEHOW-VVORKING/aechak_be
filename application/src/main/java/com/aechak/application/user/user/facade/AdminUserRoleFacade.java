package com.aechak.application.user.user.facade;

import com.aechak.application.support.PageQuery;
import com.aechak.application.support.PageResult;
import com.aechak.application.user.user.service.AdminUserRoleService;
import com.aechak.application.user.user.usecase.AdminUserRoleUseCase;
import com.aechak.application.user.user.usecase.result.AdminUserRoleResult;
import com.aechak.common.error.BusinessException;
import com.aechak.domain.user.error.UserErrorCode;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** AdminUserRoleUseCase의 유일한 구현체. 트랜잭션 경계는 여기 고정. */
@Slf4j
@Service
public class AdminUserRoleFacade implements AdminUserRoleUseCase {

    private final AdminUserRoleService adminUserRoleService;
    private final TransactionTemplate tx;

    public AdminUserRoleFacade(
            AdminUserRoleService adminUserRoleService, PlatformTransactionManager transactionManager) {
        this.adminUserRoleService = adminUserRoleService;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    @Override
    public PageResult<AdminUserRoleResult> listAdmins(PageQuery pageQuery) {
        return new PageResult<>(
                adminUserRoleService.findAdminPage(pageQuery).stream()
                        .map(AdminUserRoleResult::from)
                        .toList(),
                adminUserRoleService.countAdmins());
    }

    @Override
    public void promote(long adminId, long targetUserId) {
        tx.executeWithoutResult(txStatus -> adminUserRoleService.promote(adminId, targetUserId));
        log.info("관리자 승격 adminId={} targetUserId={}", adminId, targetUserId);
    }

    @Override
    public void demote(long adminId, long targetUserId) {
        tx.executeWithoutResult(txStatus -> adminUserRoleService.demote(adminId, targetUserId));
        log.info("관리자 강등 adminId={} targetUserId={}", adminId, targetUserId);
    }

    /** 대상별로 트랜잭션을 열어 1건의 실패가 나머지 대상을 되돌리지 않게 한다. */
    @Override
    public void ensureBootstrapAdmins(List<Long> userIds) {
        for (Long userId : userIds) {
            promoteForBootstrap(userId);
        }
    }

    private void promoteForBootstrap(long userId) {
        try {
            tx.executeWithoutResult(txStatus -> adminUserRoleService.promoteWithoutActor(userId));
            log.info("기동 부트스트랩으로 관리자를 승격했습니다 userId={}", userId);
        } catch (BusinessException e) {
            if (e.getErrorCode() == UserErrorCode.USER_ALREADY_ADMIN) {
                log.debug("기동 부트스트랩 대상이 이미 관리자입니다 userId={}", userId);
                return;
            }
            // 대상 1건의 실패로 부팅을 끊지 않는다
            log.warn(
                    "기동 부트스트랩 관리자 승격에 실패했습니다 userId={} errorCode={}",
                    userId,
                    e.getErrorCode().getCode());
        } catch (RuntimeException e) {
            log.warn("기동 부트스트랩 관리자 승격에 실패했습니다 userId={}", userId, e);
        }
    }
}
