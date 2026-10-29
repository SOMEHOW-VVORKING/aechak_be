package com.aechak.admin.claim.request;

import com.aechak.application.order.claim.admin.usecase.command.InterveneClaimCommand;
import com.aechak.domain.order.claim.ClaimIntervention;
import com.aechak.domain.order.claim.enums.ClaimFaultDecision;
import com.aechak.domain.order.claim.enums.ClaimInterventionReason;
import com.aechak.domain.order.claim.enums.ReturnCostBearer;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 운영자 개입 요청.
 *
 * <p>판정과 사유의 조합 규칙(판정별로 고를 수 있는 사유, 기타의 상세 필수)은 형식이 아니라 업무 규칙이라
 * 도메인이 거절한다 — 여기서는 필수 여부와 길이만 본다.
 *
 * <p>returnCostBearer를 비우면 판정의 기본값(셀러 귀책=셀러 부담, 구매자 귀책=구매자 부담)이 들어간다.
 * 취소 클레임은 반품 발송이 없어 어느 값을 보내도 비워진다.
 */
public record InterveneClaimRequest(
        @NotNull(message = "귀책 판정을 선택해 주세요.") ClaimFaultDecision faultDecision,
        @NotNull(message = "개입 사유를 선택해 주세요.") ClaimInterventionReason reason,
        @Size(max = ClaimIntervention.REASON_DETAIL_MAX_LENGTH, message = "판정 사유는 {max}자 이하여야 합니다.")
                String reasonDetail,
        ReturnCostBearer returnCostBearer,
        @Size(max = ClaimIntervention.INTERNAL_MEMO_MAX_LENGTH, message = "내부 메모는 {max}자 이하여야 합니다.")
                String internalMemo) {

    public InterveneClaimCommand toCommand(long adminId, String claimPublicId) {
        return new InterveneClaimCommand(
                adminId, claimPublicId, faultDecision, reason, reasonDetail, returnCostBearer, internalMemo);
    }
}
