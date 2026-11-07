package com.aechak.application.product.product.admin.port;

import com.aechak.domain.product.product.enums.InspectionStatus;
import com.aechak.domain.product.product.enums.SaleStatus;

/**
 * 어드민 상품 목록 조건의 영속 계층 어휘.
 *
 * @param inspectionStatus 검수 상태. null이면 조건 없음
 * @param saleStatus 판매 상태. null이면 조건 없음
 * @param categoryId 카테고리. 그 노드와 하위 전부를 포함한다(깊이 3이라 자신·부모·조부모로 덮는다)
 * @param keyword 상품명 또는 셀러 상호명 부분일치. null이면 조건 없음
 */
public record AdminProductPageCondition(
        InspectionStatus inspectionStatus,
        SaleStatus saleStatus,
        Long categoryId,
        String keyword,
        long offset,
        int limit) {}
