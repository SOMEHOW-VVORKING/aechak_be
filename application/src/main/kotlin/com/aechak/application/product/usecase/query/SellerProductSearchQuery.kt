package com.aechak.application.product.usecase.query

import com.aechak.application.product.port.SellerProductSort
import com.aechak.application.product.port.SellerProductStockFilter
import com.aechak.domain.product.product.enums.InspectionStatus
import com.aechak.domain.product.product.enums.SaleStatus
import java.time.LocalDate

/** 셀러 상품 목록 조회 입력 — 옵셔널 필터 조합 + 정렬 + 오프셋 페이징. 상태 필터는 비어 있으면 전체 */
data class SellerProductSearchQuery(
    val sellerId: Long,
    val keyword: String? = null,
    val saleStatuses: List<SaleStatus> = emptyList(),
    val inspectionStatuses: List<InspectionStatus> = emptyList(),
    val categoryId: Long? = null,
    val createdFrom: LocalDate? = null,
    val createdTo: LocalDate? = null,
    val stockFilter: SellerProductStockFilter? = null,
    val sort: SellerProductSort = SellerProductSort.LATEST,
    val page: Int = 0,
    val size: Int = DEFAULT_SIZE,
) {
    init {
        require(page >= 0) { "page는 0 이상이어야 합니다." }
        require(size in SIZE_MIN..SIZE_MAX) { "size는 $SIZE_MIN~$SIZE_MAX 범위 안에 있어야 합니다." }
        keyword?.let {
            require(it.isNotBlank()) { "keyword는 공백일 수 없습니다." }
            require(it.length <= KEYWORD_MAX) { "keyword는 최대 $KEYWORD_MAX 자입니다." }
        }
        if (createdFrom != null && createdTo != null) {
            require(!createdFrom.isAfter(createdTo)) { "등록일 필터의 시작이 끝보다 늦을 수 없습니다." }
        }
    }

    companion object {
        const val DEFAULT_SIZE = 20
        const val KEYWORD_MAX = 100

        // @Range(min, max) 속성이 long이라 Long 유지, Int는 컴파일 불가
        const val SIZE_MIN = 1L
        const val SIZE_MAX = 100L
    }
}
