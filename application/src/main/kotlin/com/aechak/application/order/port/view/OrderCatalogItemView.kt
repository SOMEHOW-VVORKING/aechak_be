package com.aechak.application.order.port.view

import com.aechak.domain.product.product.ProductPricing
import com.aechak.domain.product.product.enums.InspectionStatus
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.seller.seller.enums.SellerStatus
import java.time.LocalDateTime

data class OrderCatalogItemView(
    val optionCombinationId: Long,
    val productId: Long,
    /** 상품별 최신 product_versions.id. 버전 행이 아직 없으면 null — 주문 불가로 처리 */
    val latestProductVersionId: Long?,
    val stockQuantity: Int,
    val optionActive: Boolean,
    val saleStatus: SaleStatus,
    val sellerStatus: SellerStatus,
    val inspectionStatus: InspectionStatus,
    val additionalPrice: Long,
    val regularPrice: Long,
    val discountPrice: Long?,
    val discountStartAt: LocalDateTime?,
    val discountEndAt: LocalDateTime?,
    val sellerId: Long,
    val storeName: String,
    val baseShippingFee: Long,
    val freeShippingThreshold: Long?,
) {
    /**
     * 셀러는 ACTIVE가 아닌 값을 전부 막음(상태가 늘 때 구멍 방지).
     * 재고는 빠른 실패용 검증일 뿐 최종 판정은 저장소의 조건부 원자 UPDATE가 함.
     */
    fun orderable(quantity: Int): Boolean =
        sellerStatus == SellerStatus.ACTIVE &&
            saleStatus.canOrder() &&
            optionActive &&
            inspectionStatus == InspectionStatus.APPROVED &&
            stockQuantity >= quantity

    fun pricing(): ProductPricing = ProductPricing(regularPrice, discountPrice, discountStartAt, discountEndAt)

    fun unitPriceAt(now: LocalDateTime): Long = pricing().sellingPriceAt(now) + additionalPrice
}
