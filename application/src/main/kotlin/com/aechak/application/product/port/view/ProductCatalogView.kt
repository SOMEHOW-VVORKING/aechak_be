package com.aechak.application.product.port.view

import com.aechak.domain.product.product.ProductPricing
import com.aechak.domain.product.product.enums.SaleStatus
import java.time.LocalDateTime

/**
 * 카탈로그 목록 조회의 읽기 모델.
 *
 * - sortPriceAtAnchor: SQL이 조회 기준 시각(now)으로 계산한 정렬·커서 경계용 유효가격
 * - 할인 원본 필드: 응답 생성 시 현재 시각으로 표시가·할인율을 다시 계산([pricing]). sortPriceAtAnchor를 표시가로
 *   그대로 쓰면 커서 순회 2페이지 이후 만료 할인가가 카드에 남으므로 분리.
 * - isViewable: 상품 상세 진입 가능 여부
 */
data class ProductCatalogView(
    val id: Long,
    val publicId: String,
    val name: String,
    val sellerName: String,
    val representativeImageKey: String?,
    val regularPrice: Long,
    val discountPrice: Long?,
    val discountStartAt: LocalDateTime?,
    val discountEndAt: LocalDateTime?,
    val sortPriceAtAnchor: Long,
    val saleStatus: SaleStatus,
    val isViewable: Boolean,
) {
    fun pricing(): ProductPricing =
        ProductPricing(
            regularPrice = regularPrice,
            discountPrice = discountPrice,
            discountStartAt = discountStartAt,
            discountEndAt = discountEndAt,
        )
}
