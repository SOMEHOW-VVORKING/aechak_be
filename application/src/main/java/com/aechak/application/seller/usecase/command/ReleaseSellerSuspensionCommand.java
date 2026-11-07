package com.aechak.application.seller.usecase.command;

/** 판매정지 해제 요청 — 고정 어휘 사유가 없는 조치라 상세 사유와 메모만 받는다. 둘 다 생략할 수 있다. */
public record ReleaseSellerSuspensionCommand(long adminId, long sellerId, String reasonDetail, String memo) {}
