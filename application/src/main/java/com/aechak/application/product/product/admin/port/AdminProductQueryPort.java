package com.aechak.application.product.product.admin.port;

import com.aechak.application.product.product.admin.port.view.AdminProductView;
import java.util.List;

/** 어드민 상품 전수 조회 포트. 구매자·셀러센터 조회 포트와 가른다 — 노출 조건을 걸지 않는 유일한 경로다. */
public interface AdminProductQueryPort {

    /** 상품 식별자 내림차순(등록 역순). products.id가 PK라 중복·누락이 없는 유일한 키다. */
    List<AdminProductView> findProductPage(AdminProductPageCondition condition);

    /** 필터 기준 전체 행 수. */
    long countProducts(AdminProductPageCondition condition);
}
