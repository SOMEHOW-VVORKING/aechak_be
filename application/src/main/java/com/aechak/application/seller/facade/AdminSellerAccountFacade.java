package com.aechak.application.seller.facade;

import com.aechak.application.seller.service.AdminSellerAccountService;
import com.aechak.application.seller.usecase.AdminSellerAccountUseCase;
import com.aechak.application.seller.usecase.command.ReleaseSellerSuspensionCommand;
import com.aechak.application.seller.usecase.command.SaveSellerMemoCommand;
import com.aechak.application.seller.usecase.command.SuspendSellerCommand;
import com.aechak.application.seller.usecase.query.AdminSellerSearchQuery;
import com.aechak.application.seller.usecase.result.AdminSellerDetailResult;
import com.aechak.application.seller.usecase.result.AdminSellerSummaryResult;
import com.aechak.application.support.PageResult;
import com.aechak.common.error.BusinessException;
import com.aechak.domain.seller.error.SellerErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * AdminSellerAccountUseCase의 유일한 구현체. @Transactional 경계는 여기 고정.
 *
 * <p>쓰기 셋은 TransactionTemplate(프로그램적 경계)을 쓴다: Seller에 @Version이 붙어 있어 동시 조치의
 * 낙관적 락은 커밋 시점 flush에서 터지고 선언적 경계 안의 catch로는 잡을 수 없다 — execute 밖
 * 캐치에서 10009(상태 충돌)로 번역한다(AdminSellerReviewFacade의 10101 번역과 같은 방식).
 */
@Slf4j
@Service
public class AdminSellerAccountFacade implements AdminSellerAccountUseCase {

    /** 상세 화면이 보여 주는 이력 상한 — 운영 판단에 필요한 최근 이력만 싣는다(회원 상세와 같은 값). */
    private static final int SANCTION_HISTORY_LIMIT = 50;

    private final AdminSellerAccountService adminSellerAccountService;
    private final TransactionTemplate tx;

    public AdminSellerAccountFacade(
            AdminSellerAccountService adminSellerAccountService, PlatformTransactionManager transactionManager) {
        this.adminSellerAccountService = adminSellerAccountService;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    @Override
    public PageResult<AdminSellerSummaryResult> list(AdminSellerSearchQuery query) {
        return new PageResult<>(
                adminSellerAccountService.findSellerPage(query).stream()
                        .map(AdminSellerSummaryResult::from)
                        .toList(),
                adminSellerAccountService.countSellers(query));
    }

    @Transactional(readOnly = true)
    @Override
    public AdminSellerDetailResult detail(long sellerId) {
        return AdminSellerDetailResult.of(
                adminSellerAccountService.findSeller(sellerId),
                adminSellerAccountService.findSanctionHistory(sellerId, SANCTION_HISTORY_LIMIT));
    }

    @Override
    public void suspend(SuspendSellerCommand command) {
        writeTranslatingConflicts(() -> tx.executeWithoutResult(txStatus -> adminSellerAccountService.suspend(command)));
        log.info(
                "셀러 판매정지 adminId={} sellerId={} reasonCode={} durationDays={}",
                command.adminId(),
                command.sellerId(),
                command.reasonCode(),
                command.durationDays());
    }

    @Override
    public void releaseSuspension(ReleaseSellerSuspensionCommand command) {
        writeTranslatingConflicts(
                () -> tx.executeWithoutResult(txStatus -> adminSellerAccountService.releaseSuspension(command)));
        log.info("셀러 판매정지 해제 adminId={} sellerId={}", command.adminId(), command.sellerId());
    }

    @Override
    public void saveMemo(SaveSellerMemoCommand command) {
        writeTranslatingConflicts(
                () -> tx.executeWithoutResult(txStatus -> adminSellerAccountService.saveMemo(command)));
    }

    /**
     * 동시 조치 충돌의 번역 — 계약상 10009(409).
     * 메모 저장도 같은 행의 version을 올리므로 같은 경로를 탄다.
     */
    private void writeTranslatingConflicts(Runnable block) {
        try {
            block.run();
        } catch (OptimisticLockingFailureException e) {
            // 상대 조치가 먼저 커밋된 뒤 내 flush가 나간 경우 — 운영자 둘이 정지와 해제를 동시에 누른 상태다
            throw new BusinessException(SellerErrorCode.SELLER_STATUS_TRANSITION_NOT_ALLOWED, e, null);
        }
    }
}
