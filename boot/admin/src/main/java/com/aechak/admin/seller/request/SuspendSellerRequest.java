package com.aechak.admin.seller.request;

import com.aechak.domain.seller.sanction.SellerSanction;
import com.aechak.domain.seller.sanction.enums.SellerSanctionReasonCode;
import com.aechak.domain.seller.seller.Seller;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 판매정지 요청.
 *
 * <p>durationDays를 생략하면 무기한 정지다 — 화면은 3·7·30일만 고르게 하지만 기간 없는 정지도 받는다.
 * 만료 시각을 클라이언트가 보내지 않는 이유는 클라이언트 시계를 믿지 않기 위해서다.
 * 기타 사유에 상세가 필수인 판정은 도메인이 한다(10010) — 여기서는 저장 한계만 막는다.
 */
public record SuspendSellerRequest(
        @NotNull SellerSanctionReasonCode reasonCode,
        @Size(max = SellerSanction.REASON_DETAIL_MAX_LENGTH) String reasonDetail,
        @Min(1) @Max(MAX_DURATION_DAYS) Integer durationDays,
        @Size(max = Seller.ADMIN_MEMO_MAX_LENGTH) String memo) {

    /** 1년을 넘는 정지는 사실상 퇴점이라 이 경로로 받지 않는다(회원 임시 정지와 같은 상한). */
    public static final long MAX_DURATION_DAYS = 365L;
}
