package com.aechak.application.seller.port;

import com.aechak.domain.seller.application.enums.BusinessType;
import com.aechak.domain.seller.seller.enums.SellerStatus;
import java.util.List;

/**
 * 어드민 셀러 목록 조건의 영속 계층 어휘.
 *
 * @param status 운영 상태. null이면 상태 조건 없음
 * @param businessTypes 사업자 유형. 비면 유형 조건 없음. 비어 있지 않으면 신청서가 없는 셀러는 빠진다
 * @param storeNameKeyword 상호명 부분일치. null이면 검색 조건 없음
 */
public record AdminSellerPageCondition(
        SellerStatus status, List<BusinessType> businessTypes, String storeNameKeyword, long offset, int limit) {}
