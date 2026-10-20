package com.aechak.seller.order.request

import com.aechak.application.order.claim.usecase.query.SellerClaimSearchQuery
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.claim.enums.RefundStatus
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.PositiveOrZero
import org.hibernate.validator.constraints.Range
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * 셀러 클레임 목록 조회 파라미터. 상태 필터는 응답에 나가는 enum 이름을 그대로 받는다(복수 지정 가능) —
 * 화면 표시 상태로 접는 판정은 프론트에 있으므로 서버는 축 어휘만 안다.
 */
data class SellerClaimSearchRequest(
    @field:ArraySchema(schema = Schema(allowableValues = ["CANCEL", "RETURN", "EXCHANGE"]))
    val claimType: List<String> = emptyList(),
    @field:ArraySchema(
        schema =
            Schema(
                allowableValues = [
                    "CLAIM_REQUESTED", "COLLECTING", "APPROVED", "REJECTED",
                    "ADMIN_OVERRIDDEN", "REFUND_FAILED", "WITHDRAWN",
                ],
            ),
    )
    val status: List<String> = emptyList(),
    @field:ArraySchema(schema = Schema(allowableValues = ["NONE", "REQUESTED", "DONE", "REFUND_FAILED"]))
    val refundStatus: List<String> = emptyList(),
    val requestedFrom: String? = null,
    val requestedTo: String? = null,
    @field:PositiveOrZero(message = "page는 0 이상이어야 합니다.")
    val page: Int = 0,
    @field:Range(
        min = SellerClaimSearchQuery.SIZE_MIN,
        max = SellerClaimSearchQuery.SIZE_MAX,
        message = "size는 {min}~{max} 사이여야 합니다.",
    )
    val size: Int = SellerClaimSearchQuery.DEFAULT_SIZE,
) {
    fun toQuery(sellerId: Long): SellerClaimSearchQuery {
        val from = requestedFrom?.let { parseDate("requestedFrom", it) }
        val to = requestedTo?.let { parseDate("requestedTo", it) }
        if (from != null && to != null && from.isAfter(to)) {
            throw BusinessException(CommonErrorCode.INVALID_REQUEST, detail = "요청일 시작일은 종료일보다 늦을 수 없습니다.")
        }
        return SellerClaimSearchQuery(
            sellerId = sellerId,
            claimTypes = claimType.map { parseEnum<ClaimType>("claimType", it) },
            statuses = status.map { parseEnum<ClaimStatus>("status", it) },
            refundStatuses = refundStatus.map { parseEnum<RefundStatus>("refundStatus", it) },
            requestedFrom = from,
            requestedTo = to,
            page = page,
            size = size,
        )
    }

    private inline fun <reified E : Enum<E>> parseEnum(
        field: String,
        value: String,
    ): E =
        try {
            enumValueOf<E>(value)
        } catch (e: IllegalArgumentException) {
            throw BusinessException(CommonErrorCode.INVALID_REQUEST, detail = "$field: 지원하지 않는 값입니다.")
        }

    /**
     * 네 자리 연도만 받는다 — `LocalDate.parse`는 `+999999999-12-31` 같은 확장 연도도 받아들이고,
     * 그 값이 넘어가면 반개구간 상한을 만드는 하루 더하기가 LocalDate 경계에서 터져 500이 된다.
     */
    private fun parseDate(
        field: String,
        value: String,
    ): LocalDate {
        val date =
            try {
                LocalDate.parse(value)
            } catch (e: DateTimeParseException) {
                throw BusinessException(CommonErrorCode.INVALID_REQUEST, detail = "$field: 날짜 형식이 올바르지 않습니다. (YYYY-MM-DD)")
            }
        if (date.year !in SUPPORTED_YEARS) {
            throw BusinessException(CommonErrorCode.INVALID_REQUEST, detail = "$field: 날짜 형식이 올바르지 않습니다. (YYYY-MM-DD)")
        }
        return date
    }

    private companion object {
        val SUPPORTED_YEARS = 1..9999
    }
}
