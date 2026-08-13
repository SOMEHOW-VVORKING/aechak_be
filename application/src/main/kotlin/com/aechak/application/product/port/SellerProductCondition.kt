package com.aechak.application.product.port

import com.aechak.domain.product.product.enums.InspectionStatus
import com.aechak.domain.product.product.enums.SaleStatus
import java.time.LocalDateTime

/**
 * 셀러 상품 목록 조회 조건
 *
 * - sellerId로 잠가 본인 상품만 조회한다 — 공개 카탈로그와 달리 노출 조건(검수·판매 상태 등)은 걸지 않는다
 * - 상태 필터 목록이 비어 있으면 해당 조건 미적용(전체)
 * - createdToExclusive: 등록일 상한(미포함) — 날짜 필터를 [from, to+1일) 반개구간으로 환산한 값
 * - now: 가격순 정렬의 유효가(할인 적용가) 판정 기준 시각
 */
data class SellerProductCondition(
    val sellerId: Long,
    val keyword: String?,
    val saleStatuses: List<SaleStatus>,
    val inspectionStatuses: List<InspectionStatus>,
    val categoryId: Long?,
    val createdFrom: LocalDateTime?,
    val createdToExclusive: LocalDateTime?,
    val stockFilter: SellerProductStockFilter?,
    val sort: SellerProductSort,
    val offset: Long,
    val limit: Int,
    val now: LocalDateTime,
) {
    init {
        require(offset >= 0) { "offset은 0 이상이어야 합니다." }
        require(limit > 0) { "limit은 양수여야 합니다." }
    }
}
