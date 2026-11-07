package com.aechak.admin.seller.request;

import com.aechak.domain.seller.seller.Seller;
import jakarta.validation.constraints.Size;

/** 셀러 내부 메모 저장 요청 — 덮어쓰기다. memo를 비우거나 생략하면 메모를 지운다. */
public record SaveSellerMemoRequest(@Size(max = Seller.ADMIN_MEMO_MAX_LENGTH) String memo) {}
