package com.aechak.seller.settlement.request

import com.aechak.application.settlement.port.SellerSettlementStatus
import com.aechak.application.settlement.seller.usecase.query.SellerSettlementSearchQuery
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.PositiveOrZero
import org.hibernate.validator.constraints.Range

/**
 * 셀러 정산 목록 조회 요청 파라미터.
 * month는 정산월(`yyyy-MM`), status는 화면 어휘(소문자)를 받는다. 둘 다 생략하면 전체 조회다 —
 * 화면의 '전체' 선택은 month 파라미터를 보내지 않는 것으로 표현한다.
 */
data class SellerSettlementSearchRequest(
    @field:Pattern(
        regexp = SellerSettlementSearchQuery.MONTH_REGEX,
        message = "month는 YYYY-MM 형식이어야 합니다.",
    )
    val month: String? = null,
    @field:Schema(allowableValues = ["pending", "scheduled", "completed", "held"])
    val status: String? = null,
    @field:PositiveOrZero(message = "page는 0 이상이어야 합니다.")
    val page: Int = 0,
    @field:Range(
        min = SellerSettlementSearchQuery.SIZE_MIN,
        max = SellerSettlementSearchQuery.SIZE_MAX,
        message = "size는 {min}~{max} 사이여야 합니다.",
    )
    val size: Int = SellerSettlementSearchQuery.DEFAULT_SIZE,
) {
    fun toQuery(sellerId: Long): SellerSettlementSearchQuery =
        SellerSettlementSearchQuery(
            sellerId = sellerId,
            settlementMonth = month,
            status = status?.let(::parseStatus),
            page = page,
            size = size,
        )

    private fun parseStatus(value: String): SellerSettlementStatus =
        when (value) {
            "pending" -> {
                SellerSettlementStatus.PENDING
            }

            "scheduled" -> {
                SellerSettlementStatus.SCHEDULED
            }

            "completed" -> {
                SellerSettlementStatus.COMPLETED
            }

            "held" -> {
                SellerSettlementStatus.HELD
            }

            else -> {
                throw BusinessException(
                    CommonErrorCode.INVALID_REQUEST,
                    detail = "status: 정산 대기, 정산 예정, 정산 완료, 정산 보류만 지원합니다.",
                )
            }
        }
}
