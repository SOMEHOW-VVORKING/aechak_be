package com.aechak.admin.seller.request;

import com.aechak.domain.seller.sanction.SellerSanction;
import com.aechak.domain.seller.seller.Seller;
import jakarta.validation.constraints.Size;

/** 판매정지 해제 요청 — 고정 어휘 사유가 없는 조치라 상세 사유와 메모만 받는다. 둘 다 생략할 수 있다. */
public record ReleaseSellerSuspensionRequest(
        @Size(max = SellerSanction.REASON_DETAIL_MAX_LENGTH) String reasonDetail,
        @Size(max = Seller.ADMIN_MEMO_MAX_LENGTH) String memo) {}
