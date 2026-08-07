package com.aechak.application.product.port.view

import com.aechak.domain.product.product.ProductPricing
import com.aechak.domain.product.product.enums.SaleStatus
import java.time.LocalDateTime

/**
 * 카탈로그 목록 조회 읽기 모델.
 *
 * sortPriceAtAnchor, popularityScore는 SQL이 계산한 정렬 및 커서 경계용 값(표시용 아님).
 * 표시용 별점, 리뷰 수, 할인가는 Facade와 [pricing]이 응답 시각 기준으로 채움.
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
    val popularityScore: Int,
) {
    /** 정렬키가 필요 없는 조회(카탈로그, 상세 등)용 보조 생성자. popularityScore=0 */
    constructor(
        id: Long,
        publicId: String,
        name: String,
        sellerName: String,
        representativeImageKey: String?,
        regularPrice: Long,
        discountPrice: Long?,
        discountStartAt: LocalDateTime?,
        discountEndAt: LocalDateTime?,
        sortPriceAtAnchor: Long,
        saleStatus: SaleStatus,
    ) : this(
        id,
        publicId,
        name,
        sellerName,
        representativeImageKey,
        regularPrice,
        discountPrice,
        discountStartAt,
        discountEndAt,
        sortPriceAtAnchor,
        saleStatus,
        0,
    )

    fun pricing(): ProductPricing =
        ProductPricing(
            regularPrice = regularPrice,
            discountPrice = discountPrice,
            discountStartAt = discountStartAt,
            discountEndAt = discountEndAt,
        )
}
