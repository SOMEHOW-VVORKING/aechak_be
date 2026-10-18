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

/**
 * AdminUserRoleUseCase의 유일한 구현체. @Transactional 경계는 여기 고정.
 *
 * <p>역할 변경은 TransactionTemplate(프로그램적 경계)을 쓴다: 부트스트랩이 대상 1건의 실패에도
 * 나머지 대상을 계속 처리해야 하므로 대상별로 경계를 열고 닫아야 하고, 커밋 시점 flush에서 터지는
 * 예외를 execute 밖에서 잡아야 한다.
 */
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
        tx.executeWithoutResult(txStatus -> adminUserRoleService.promote(targetUserId));
        // 경량 감사 — 역할 변경 이력 테이블 대신 구조화 로그
        log.info("관리자 승격 adminId={} targetUserId={}", adminId, targetUserId);
    }

    @Override
    public void demote(long adminId, long targetUserId) {
        tx.executeWithoutResult(txStatus -> adminUserRoleService.demote(targetUserId));
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
            tx.executeWithoutResult(txStatus -> adminUserRoleService.promote(userId));
            log.info("기동 부트스트랩으로 관리자를 승격했습니다 userId={}", userId);
        } catch (BusinessException e) {
            if (e.getErrorCode() == UserErrorCode.USER_ALREADY_ADMIN) {
                log.debug("기동 부트스트랩 대상이 이미 관리자입니다 userId={}", userId);
                return;
            }
            // 명부에 적힌 계정이 사라졌더라도 부팅은 계속한다 — 어드민이 뜨지 않으면 명부를 고칠 수단도 사라진다
            log.warn(
                    "기동 부트스트랩 관리자 승격에 실패했습니다 userId={} errorCode={}",
                    userId,
                    e.getErrorCode().getCode());
        } catch (RuntimeException e) {
            log.warn("기동 부트스트랩 관리자 승격에 실패했습니다 userId={}", userId, e);
        }
    }
}
