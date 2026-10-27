package com.aechak.admin.order.request;

import com.aechak.application.order.admin.usecase.query.AdminOrderMetricsQuery;
import com.aechak.common.error.BusinessException;
import com.aechak.common.error.CommonErrorCode;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 어드민 주문 지표 파라미터. 목록과 달리 상태·지연·키워드를 받지 않는다 —
 * 카드가 그 축으로 모수를 쪼개 보여주므로 상태로 좁히면 카드 넷 중 셋이 0으로 눌린다.
 */
public record AdminOrderMetricsRequest(
        @Positive(message = "sellerId는 양수여야 합니다.") Long sellerId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate orderedFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate orderedTo) {

    public AdminOrderMetricsQuery toQuery() {
        if (orderedFrom != null && orderedTo != null && orderedFrom.isAfter(orderedTo)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, null, "주문일 시작일은 종료일보다 늦을 수 없습니다.");
        }
        return new AdminOrderMetricsQuery(sellerId, orderedFrom, orderedTo);
    }
}
