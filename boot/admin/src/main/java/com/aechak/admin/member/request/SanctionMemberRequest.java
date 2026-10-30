package com.aechak.admin.member.request;

import com.aechak.domain.user.sanction.enums.SanctionAction;
import com.aechak.domain.user.sanction.enums.SanctionReasonCode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 제재 적용 요청.
 *
 * <p>durationDays는 임시 정지에만 쓴다 — 조치별 필수/금지 판정은 도메인이 하고(30801·30802),
 * 여기서는 형식 범위만 막는다. 만료 시각을 클라이언트가 보내지 않는 이유는 클라이언트 시계를 믿지 않기 위해서다.
 */
public record SanctionMemberRequest(
        @NotNull SanctionAction action,
        @NotNull SanctionReasonCode reasonCode,
        @Size(max = 500) String reasonDetail,
        @Min(1) @Max(365) Integer durationDays,
        @Size(max = 300) String memo) {}
