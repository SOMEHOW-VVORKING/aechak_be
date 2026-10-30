package com.aechak.admin.audit.response;

import com.aechak.application.audit.usecase.result.AdminAuditLogListResult;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 감사 로그 목록. {@code totalCount}·{@code recordingSince}는 첫 페이지(커서 없음)에만 실린다.
 *
 * <p>{@code recordingSince}는 <b>기록이 시작된 시점</b>이다. 소급 기록이 불가능하므로 그 이전의
 * 운영 조치는 이 로그에 없다 — 화면이 "조치가 없었다"와 "그때는 아직 기록하지 않았다"를 구별할 수
 * 있어야 해서 내려준다. 필터와 무관한 전체 기준이고, 행이 하나도 없으면 null이다.
 */
public record AdminAuditLogListResponse(
        List<AdminAuditLogItemResponse> items,
        Long totalCount,
        String nextCursor,
        boolean hasNext,
        LocalDateTime recordingSince) {

    public static AdminAuditLogListResponse from(AdminAuditLogListResult result) {
        return new AdminAuditLogListResponse(
                result.getPage().getItems().stream()
                        .map(AdminAuditLogItemResponse::from)
                        .toList(),
                result.getPage().getTotalCount(),
                result.getPage().getNextCursor(),
                result.getPage().getHasNext(),
                result.getRecordingSince());
    }
}
