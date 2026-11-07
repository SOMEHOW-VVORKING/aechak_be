package com.aechak.application.product.product.admin.service;

import com.aechak.application.audit.support.AdminAuditContextHolder;
import com.aechak.application.product.product.admin.port.AdminProductPageCondition;
import com.aechak.application.product.product.admin.port.AdminProductQueryPort;
import com.aechak.application.product.product.admin.port.view.AdminProductView;
import com.aechak.application.product.product.admin.usecase.command.ResumeProductSaleCommand;
import com.aechak.application.product.product.admin.usecase.command.SuspendProductSaleCommand;
import com.aechak.application.product.product.admin.usecase.query.AdminProductSearchQuery;
import com.aechak.common.error.BusinessException;
import com.aechak.domain.product.error.ProductErrorCode;
import com.aechak.domain.product.option.repository.OptionCombinationRepository;
import com.aechak.domain.product.product.Product;
import com.aechak.domain.product.product.repository.ProductRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 어드민 상품 관리 로직 보관함 — Facade에서만 호출된다. 전역 자원 접근이라 소유권 검증이 없다. */
@Service
@RequiredArgsConstructor
public class AdminProductManagementService {

    private final ProductRepository productRepository;
    private final OptionCombinationRepository optionCombinationRepository;
    private final AdminProductQueryPort adminProductQueryPort;

    public List<AdminProductView> findProductPage(AdminProductSearchQuery query) {
        return adminProductQueryPort.findProductPage(toCondition(query));
    }

    public long countProducts(AdminProductSearchQuery query) {
        return adminProductQueryPort.countProducts(toCondition(query));
    }

    public void suspendSale(SuspendProductSaleCommand command) {
        loadProduct(command.productPublicId()).blockSaleByAdmin(command.adminId(), command.reason().strip());
    }

    /**
     * 재개. 막기 직전 상태로 되돌린 뒤 재고 동기화를 이어 부른다 — 막혀 있던 사이에 재고가 비었거나
     * 들어왔을 수 있고, 품절은 셀러가 지정하는 값이 아니라 재고에서 파생하는 값이다(셀러 토글과 같은 순서).
     */
    public void resumeSale(ResumeProductSaleCommand command) {
        Product product = loadProduct(command.productPublicId());
        product.unblockSaleByAdmin();
        product.syncSaleStatusWithStock(optionCombinationRepository.existsActiveStock(product.getId()));
    }

    /**
     * 조치 공통 선행 조회. 감사 행에 남길 그 시점의 상품명도 여기서 집는다 — 조치보다 먼저 올려 둬야
     * 거부당한 시도의 행에도 대상 이름이 남는다.
     */
    private Product loadProduct(String productPublicId) {
        Long productId = productRepository.findIdByPublicId(productPublicId);
        Product product = productId == null ? null : productRepository.findById(productId);
        if (product == null) {
            throw new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND, null, null);
        }
        AdminAuditContextHolder.INSTANCE.label(product.getName());
        return product;
    }

    private AdminProductPageCondition toCondition(AdminProductSearchQuery query) {
        return new AdminProductPageCondition(
                query.inspectionStatus(),
                query.saleStatus(),
                query.categoryId(),
                query.keyword(),
                (long) query.page() * query.size(),
                query.size());
    }
}
