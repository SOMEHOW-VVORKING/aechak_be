package com.aechak.application.product.product.admin.usecase;

import com.aechak.application.product.product.admin.usecase.command.ResumeProductSaleCommand;
import com.aechak.application.product.product.admin.usecase.command.SuspendProductSaleCommand;
import com.aechak.application.product.product.admin.usecase.query.AdminProductSearchQuery;
import com.aechak.application.product.product.admin.usecase.result.AdminProductSummaryResult;
import com.aechak.application.support.PageResult;

/**
 * 어드민 상품 카탈로그 관리. 자격(role=ADMIN)은 어드민 모듈 게이트가 보증한다.
 *
 * <p>검수 큐(판정 대기열)와 가른다 — 여기는 큐에 없는 상품도 보고, 검수와 무관한 이유로 판매를
 * 막는 곳이다. 검수 통과·반려는 검수 큐 유스케이스가 그대로 소유한다.
 */
public interface AdminProductUseCase {

    /** 등록 역순. 노출 조건을 걸지 않아 검수 반려·판매중지 상품도 함께 나온다. */
    PageResult<AdminProductSummaryResult> list(AdminProductSearchQuery query);

    /** 판매중지 — 판매 상태를 SUSPENDED로 옮기고 막음 표시를 남겨 셀러가 되돌리지 못하게 한다. */
    void suspendSale(SuspendProductSaleCommand command);

    /** 재개 — 막음 표시를 지우고 막기 직전 상태로 되돌린 뒤 재고에서 품절을 다시 파생한다. */
    void resumeSale(ResumeProductSaleCommand command);
}
