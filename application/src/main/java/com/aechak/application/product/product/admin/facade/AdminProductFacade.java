package com.aechak.application.product.product.admin.facade;

import com.aechak.application.product.product.admin.service.AdminProductManagementService;
import com.aechak.application.product.product.admin.usecase.AdminProductUseCase;
import com.aechak.application.product.product.admin.usecase.command.ResumeProductSaleCommand;
import com.aechak.application.product.product.admin.usecase.command.SuspendProductSaleCommand;
import com.aechak.application.product.product.admin.usecase.query.AdminProductSearchQuery;
import com.aechak.application.product.product.admin.usecase.result.AdminProductSummaryResult;
import com.aechak.application.support.PageResult;
import com.aechak.common.error.BusinessException;
import com.aechak.domain.product.error.ProductErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * AdminProductUseCase의 유일한 구현체. @Transactional 경계는 여기 고정.
 *
 * <p>쓰기 둘은 TransactionTemplate(프로그램적 경계)을 쓴다: Product에 @Version이 붙어 있어 동시 조치의
 * 낙관적 락은 커밋 시점 flush에서 터지고 선언적 경계 안의 catch로는 잡을 수 없다 — execute 밖
 * 캐치에서 40011(상태 충돌)로 번역한다(셀러 계정 제재의 10009 번역과 같은 방식).
 */
@Slf4j
@Service
public class AdminProductFacade implements AdminProductUseCase {

    private final AdminProductManagementService adminProductManagementService;
    private final TransactionTemplate tx;

    public AdminProductFacade(
            AdminProductManagementService adminProductManagementService,
            PlatformTransactionManager transactionManager) {
        this.adminProductManagementService = adminProductManagementService;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    @Override
    public PageResult<AdminProductSummaryResult> list(AdminProductSearchQuery query) {
        return new PageResult<>(
                adminProductManagementService.findProductPage(query).stream()
                        .map(AdminProductSummaryResult::from)
                        .toList(),
                adminProductManagementService.countProducts(query));
    }

    @Override
    public void suspendSale(SuspendProductSaleCommand command) {
        writeTranslatingConflicts(
                () -> tx.executeWithoutResult(txStatus -> adminProductManagementService.suspendSale(command)));
        log.info("상품 판매중지 adminId={} productId={}", command.adminId(), command.productPublicId());
    }

    @Override
    public void resumeSale(ResumeProductSaleCommand command) {
        writeTranslatingConflicts(
                () -> tx.executeWithoutResult(txStatus -> adminProductManagementService.resumeSale(command)));
        log.info("상품 판매 재개 adminId={} productId={}", command.adminId(), command.productPublicId());
    }

    /** 운영자 둘이 판매중지와 재개를 동시에 누른 경우 — 상대 조치가 먼저 커밋된 뒤 내 flush가 나갔다. */
    private void writeTranslatingConflicts(Runnable block) {
        try {
            block.run();
        } catch (OptimisticLockingFailureException e) {
            throw new BusinessException(ProductErrorCode.PRODUCT_SALE_BLOCK_CONFLICT, e, null);
        }
    }
}
