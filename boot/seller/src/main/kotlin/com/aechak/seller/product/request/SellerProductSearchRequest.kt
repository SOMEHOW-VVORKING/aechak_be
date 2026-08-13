package com.aechak.seller.product.request

import com.aechak.application.product.port.SellerProductSort
import com.aechak.application.product.port.SellerProductStockFilter
import com.aechak.application.product.usecase.query.SellerProductSearchQuery
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.product.product.enums.InspectionStatus
import com.aechak.domain.product.product.enums.SaleStatus
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import org.hibernate.validator.constraints.Range
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * 셀러 상품 목록 조회 요청 파라미터.
 * 상태 필터는 응답에 나가는 enum 이름을 그대로 받고(복수 지정 가능), sort·stock은 소문자 어휘를 쓴다.
 */
data class SellerProductSearchRequest(
    @field:Size(max = SellerProductSearchQuery.KEYWORD_MAX, message = "keyword는 최대 {max}자입니다.")
    val keyword: String? = null,
    val saleStatus: List<String> = emptyList(),
    val inspectionStatus: List<String> = emptyList(),
    val category: Long? = null,
    val createdFrom: String? = null,
    val createdTo: String? = null,
    val stock: String? = null,
    val sort: String = "latest",
    @field:PositiveOrZero(message = "page는 0 이상이어야 합니다.")
    val page: Int = 0,
    @field:Range(
        min = SellerProductSearchQuery.SIZE_MIN,
        max = SellerProductSearchQuery.SIZE_MAX,
        message = "size는 {min}~{max} 사이여야 합니다.",
    )
    val size: Int = SellerProductSearchQuery.DEFAULT_SIZE,
) {
    fun toQuery(sellerId: Long): SellerProductSearchQuery {
        val from = createdFrom?.let(::parseDate)
        val to = createdTo?.let(::parseDate)
        if (from != null && to != null && from.isAfter(to)) {
            throw BusinessException(CommonErrorCode.INVALID_REQUEST)
        }
        return SellerProductSearchQuery(
            sellerId = sellerId,
            keyword = keyword?.trim()?.takeIf { it.isNotEmpty() },
            saleStatuses = saleStatus.map { parseEnum<SaleStatus>(it) },
            inspectionStatuses = inspectionStatus.map { parseEnum<InspectionStatus>(it) },
            categoryId = category,
            createdFrom = from,
            createdTo = to,
            stockFilter = stock?.let(::parseStock),
            sort = parseSort(sort),
            page = page,
            size = size,
        )
    }

    private inline fun <reified E : Enum<E>> parseEnum(value: String): E =
        try {
            enumValueOf<E>(value)
        } catch (e: IllegalArgumentException) {
            throw BusinessException(CommonErrorCode.INVALID_REQUEST)
        }

    private fun parseDate(value: String): LocalDate =
        try {
            LocalDate.parse(value)
        } catch (e: DateTimeParseException) {
            throw BusinessException(CommonErrorCode.INVALID_REQUEST)
        }

    private fun parseStock(value: String): SellerProductStockFilter =
        when (value) {
            "in_stock" -> SellerProductStockFilter.IN_STOCK
            "sold_out" -> SellerProductStockFilter.SOLD_OUT
            else -> throw BusinessException(CommonErrorCode.INVALID_REQUEST)
        }

    private fun parseSort(value: String): SellerProductSort =
        when (value) {
            "latest" -> SellerProductSort.LATEST
            "price_asc" -> SellerProductSort.PRICE_ASC
            "price_desc" -> SellerProductSort.PRICE_DESC
            else -> throw BusinessException(CommonErrorCode.INVALID_REQUEST)
        }
}
