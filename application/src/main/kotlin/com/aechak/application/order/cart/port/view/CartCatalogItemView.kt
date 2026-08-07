package com.aechak.application.order.cart.port.view

import com.aechak.domain.order.cart.enums.CartItemStatus
import com.aechak.domain.product.product.ProductPricing
import com.aechak.domain.product.product.enums.InspectionStatus
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.seller.seller.enums.SellerStatus
import java.time.LocalDateTime

data class CartCatalogItemView(
    val optionCombinationId: Long,
    val productPublicId: String,
    val stockQuantity: Int,
    val optionActive: Boolean,
    val saleStatus: SaleStatus,
    val sellerStatus: SellerStatus,
    val inspectionStatus: InspectionStatus,
    val optionName: String,
    val additionalPrice: Long,
    val productName: String,
    val representativeImageKey: String?,
    val regularPrice: Long,
    val discountPrice: Long?,
    val discountStartAt: LocalDateTime?,
    val discountEndAt: LocalDateTime?,
    val sellerId: Long,
    val storeName: String,
    val baseShippingFee: Long,
    val freeShippingThreshold: Long?,
) {
    fun approved(): Boolean = inspectionStatus == InspectionStatus.APPROVED

    /**
     * 먼저 걸리는 값을 씀. 셀러는 ACTIVE가 아닌 값을 전부 막는데, 열거하면 상태가 늘 때 구멍이 생기기 때문.
     * 판매 상태는 반대로 막을 값만 열거함. 상품의 OUT_OF_STOCK은 상품 전체 신호라 조합 재고로 따로 판정.
     */
    fun itemStatus(): CartItemStatus =
        when {
            sellerStatus != SellerStatus.ACTIVE -> CartItemStatus.SELLER_UNAVAILABLE
            saleStatus == SaleStatus.ENDED -> CartItemStatus.SALE_ENDED
            saleStatus == SaleStatus.SUSPENDED -> CartItemStatus.SALE_SUSPENDED
            !optionActive -> CartItemStatus.OPTION_UNAVAILABLE
            stockQuantity == 0 -> CartItemStatus.OUT_OF_STOCK
            else -> CartItemStatus.ACTIVE
        }

    fun pricing(): ProductPricing = ProductPricing(regularPrice, discountPrice, discountStartAt, discountEndAt)

    fun originalPriceAt(): Long = regularPrice + additionalPrice

    fun unitPriceAt(now: LocalDateTime): Long = pricing().sellingPriceAt(now) + additionalPrice
}
