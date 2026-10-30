package com.aechak.admin.order.request;

import com.aechak.application.order.admin.usecase.query.AdminOrderSearchQuery;
import com.aechak.application.support.CursorPageSize;
import com.aechak.common.error.BusinessException;
import com.aechak.common.error.CommonErrorCode;
import com.aechak.domain.order.order.enums.OrderStatus;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.hibernate.validator.constraints.Range;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 어드민 주문 목록 조회 파라미터. 전부 선택이다.
 *
 * <p>status는 주문 상태 enum 이름을 그대로 받는다 — 셀러 목록처럼 세그먼트 어휘로 접지 않는다.
 * 운영자는 발송 대기와 배송예정을 묶어 보는 것이 아니라 어느 전이에서 멈췄는지를 봐야 한다.
 *
 * <p>size가 비면 기본값을 쓴다 — 레코드에는 기본값이 없어 null을 기본값 신호로 쓴다.
 */
public record AdminOrderSearchRequest(
        OrderStatus status,
        @Positive(message = "sellerId는 양수여야 합니다.") Long sellerId,
        @Size(max = 100, message = "keyword는 최대 {max}자입니다.") String keyword,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate orderedFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate orderedTo,
        Boolean delayedOnly,
        String cursor,
        @Range(min = CursorPageSize.MIN, max = CursorPageSize.MAX, message = "size는 {min}~{max} 사이여야 합니다.")
                Integer size) {

    public AdminOrderSearchQuery toQuery() {
        if (orderedFrom != null && orderedTo != null && orderedFrom.isAfter(orderedTo)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, null, "주문일 시작일은 종료일보다 늦을 수 없습니다.");
        }
        return new AdminOrderSearchQuery(
                status,
                sellerId,
                keyword,
                orderedFrom,
                orderedTo,
                Boolean.TRUE.equals(delayedOnly),
                cursor,
                size == null ? CursorPageSize.DEFAULT : size);
    }
}
