package com.aechak.seller.order.request

import com.aechak.application.order.seller.port.SellerOrderStatusFilter
import com.aechak.application.order.seller.usecase.query.SellerOrderSearchQuery
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import org.hibernate.validator.constraints.Range
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * 셀러 주문 목록 조회 요청 파라미터.
 * status는 응답 상태값(enum 이름)이 아니라 화면 세그먼트 어휘를 받는다 — 한 세그먼트가 상태 여러 개를 묶는다.
 */
data class SellerOrderSearchRequest(
    @field:Schema(
        allowableValues = ["all", "new", "awaiting-dispatch", "shipping", "delivered", "cancelled"],
        defaultValue = "all",
    )
    val status: String = SellerOrderStatusFilter.ALL.code,
    @field:Size(max = SellerOrderSearchQuery.KEYWORD_MAX, message = "keyword는 최대 {max}자입니다.")
    val keyword: String? = null,
    val orderedFrom: String? = null,
    val orderedTo: String? = null,
    @field:PositiveOrZero(message = "page는 0 이상이어야 합니다.")
    val page: Int = 0,
    @field:Range(
        min = SellerOrderSearchQuery.SIZE_MIN,
        max = SellerOrderSearchQuery.SIZE_MAX,
        message = "size는 {min}~{max} 사이여야 합니다.",
    )
    val size: Int = SellerOrderSearchQuery.DEFAULT_SIZE,
) {
    fun toQuery(sellerId: Long): SellerOrderSearchQuery {
        val from = orderedFrom?.let { parseDate("orderedFrom", it) }
        val to = orderedTo?.let { parseDate("orderedTo", it) }
        if (from != null && to != null && from.isAfter(to)) {
            throw BusinessException(CommonErrorCode.INVALID_REQUEST, detail = "주문일 시작일은 종료일보다 늦을 수 없습니다.")
        }
        return SellerOrderSearchQuery(
            sellerId = sellerId,
            filter = SellerOrderStatusFilter.from(status),
            keyword = keyword?.trim()?.takeIf { it.isNotEmpty() },
            orderedFrom = from,
            orderedTo = to,
            page = page,
            size = size,
        )
    }

    private fun parseDate(
        field: String,
        value: String,
    ): LocalDate =
        try {
            LocalDate.parse(value)
        } catch (e: DateTimeParseException) {
            throw BusinessException(CommonErrorCode.INVALID_REQUEST, detail = "$field: 날짜 형식이 올바르지 않습니다. (YYYY-MM-DD)")
        }
}
