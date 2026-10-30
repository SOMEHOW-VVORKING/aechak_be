package com.aechak.admin.order.request;

import com.aechak.application.order.admin.usecase.command.NudgeOrderDispatchCommand;
import com.aechak.domain.order.nudge.OrderDispatchNudge;
import jakarta.validation.constraints.Size;

/** 독촉 메모는 선택이다 — 사유 없이 누른 독촉도 "운영자가 봤다"는 기록으로 쓸모가 있다. */
public record NudgeOrderDispatchRequest(
        @Size(max = OrderDispatchNudge.MAX_MEMO_LENGTH, message = "독촉 메모는 {max}자 이하여야 합니다.") String memo) {

    public NudgeOrderDispatchCommand toCommand(long adminUserId, String orderPublicId) {
        return new NudgeOrderDispatchCommand(adminUserId, orderPublicId, memo);
    }
}
