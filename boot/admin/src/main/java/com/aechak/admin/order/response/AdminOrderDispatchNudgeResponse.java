package com.aechak.admin.order.response;

import com.aechak.application.order.admin.usecase.result.AdminOrderDispatchNudgeResult;
import java.time.LocalDateTime;

/**
 * 발송 독촉 기록 한 건.
 *
 * <p><b>알림 발송 여부를 담지 않는다.</b> 어드민 실행 모듈이 발송 어댑터를 조립하지 않아 이 경로는
 * 기록만 남긴다 — 보내지도 않은 것을 보냈다고 말하는 필드를 두지 않는다.
 */
public record AdminOrderDispatchNudgeResponse(long nudgeId, long adminUserId, String memo, LocalDateTime nudgedAt) {

    public static AdminOrderDispatchNudgeResponse from(AdminOrderDispatchNudgeResult result) {
        return new AdminOrderDispatchNudgeResponse(
                result.getNudgeId(), result.getAdminUserId(), result.getMemo(), result.getNudgedAt());
    }
}
