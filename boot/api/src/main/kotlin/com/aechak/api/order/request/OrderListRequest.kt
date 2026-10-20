package com.aechak.api.order.request

import com.aechak.application.order.port.OrderStatusFilter
import com.aechak.application.order.usecase.query.OrderListQuery
import io.swagger.v3.oas.annotations.media.Schema
import org.hibernate.validator.constraints.Range

data class OrderListRequest(
    // 상태 enum 이름이 아니라 화면 세그먼트 어휘다 — 한 세그먼트가 상태 여러 개를 묶는다.
    @field:Schema(allowableValues = ["all", "ongoing", "completed", "cancelled"], defaultValue = "all")
    val status: String = OrderStatusFilter.ALL.code,
    val cursor: String? = null,
    @field:Range(
        min = OrderListQuery.SIZE_MIN,
        max = OrderListQuery.SIZE_MAX,
        message = "size는 {min}~{max} 사이여야 합니다.",
    )
    val size: Int = OrderListQuery.DEFAULT_SIZE,
) {
    fun toQuery(buyerId: Long) =
        OrderListQuery(
            buyerId = buyerId,
            filter = OrderStatusFilter.from(status),
            cursor = cursor,
            size = size,
        )
}
