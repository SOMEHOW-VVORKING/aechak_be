package com.aechak.application.seller.usecase.query;

import com.aechak.domain.seller.application.enums.BusinessType;
import java.util.List;

/**
 * 운영 화면의 판매자 유형 축. 사업자와 개인 둘로 가른다 — 신청서의 세 유형(개인 일반·개인사업자·법인)은
 * 심사 화면의 어휘이고, 셀러 목록이 거르는 축은 "사업자인가"뿐이다.
 *
 * <p>응답의 businessType은 세 값 그대로 내려간다 — 거르는 축을 둘로 묶는 것과 보여 주는 값을 뭉개는 것은 다르다.
 */
public enum SellerTypeFilter {
    ALL(List.of()),
    BUSINESS(List.of(BusinessType.SOLE_PROPRIETORSHIP, BusinessType.CORPORATE)),
    INDIVIDUAL(List.of(BusinessType.PERSONAL_GENERAL));

    private final List<BusinessType> businessTypes;

    SellerTypeFilter(List<BusinessType> businessTypes) {
        this.businessTypes = businessTypes;
    }

    /** 영속 계층에 넘길 유형 목록 — 비면 유형 조건 없음이다. */
    public List<BusinessType> businessTypes() {
        return businessTypes;
    }

    /** 알 수 없는 값은 전체로 본다 — 목록 필터의 오타로 400을 내지 않는다(회원 목록과 같은 결). */
    public static SellerTypeFilter from(String raw) {
        if (raw == null || raw.isBlank()) {
            return ALL;
        }
        for (SellerTypeFilter filter : values()) {
            if (filter.name().equalsIgnoreCase(raw.strip())) {
                return filter;
            }
        }
        return ALL;
    }
}
