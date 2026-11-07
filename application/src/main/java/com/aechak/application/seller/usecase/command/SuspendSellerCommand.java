package com.aechak.application.seller.usecase.command;

import com.aechak.domain.seller.sanction.enums.SellerSanctionReasonCode;

/**
 * 판매정지 요청.
 *
 * @param durationDays 정지 기간(일). null이면 무기한 정지다 — 해제 예정 시각이 없고 운영자 해제로만 풀린다
 * @param memo 운영자 내부 메모. 있으면 이력 행에 사본으로 남고 셀러의 현재 메모를 덮어쓴다
 */
public record SuspendSellerCommand(
        long adminId,
        long sellerId,
        SellerSanctionReasonCode reasonCode,
        String reasonDetail,
        Integer durationDays,
        String memo) {}
