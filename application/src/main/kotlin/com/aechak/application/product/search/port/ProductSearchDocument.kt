package com.aechak.application.product.search.port

import com.aechak.domain.product.product.enums.InspectionStatus
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.seller.seller.enums.SellerStatus
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 검색 색인에 넣을 상품 한 건. 노출 여부와 무관하게 모든 상품을 담고 노출 조건은 검색할 때 건다
 * 셀러 열은 셀러 행이 없으면 null, 통계 열은 통계 행이 없으면 null
 */
data class ProductSearchDocument(
    val id: Long,
    val publicId: String,
    val name: String,
    val categoryId: Long,
    val sellerId: Long,
    val inspectionStatus: InspectionStatus,
    val saleStatus: SaleStatus,
    val regularPrice: Long,
    val discountPrice: Long?,
    val discountStartAt: LocalDateTime?,
    val discountEndAt: LocalDateTime?,
    val reviewCount: Int,
    val representativeImageKey: String?,
    val sellerStatus: SellerStatus?,
    val sellerBaseShippingFee: Long?,
    val storeName: String?,
    val statsReviewCount: Int?,
    val averageRating: BigDecimal?,
)
