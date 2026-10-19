package com.aechak.seller.product.response

import com.aechak.application.product.product.usecase.result.SellerProductDetailResult
import com.aechak.domain.product.product.enums.InspectionStatus
import com.aechak.domain.product.product.enums.SaleStatus
import java.time.LocalDateTime

/** 수정 폼 프리필 응답 — 이미지 키는 저장 키라 그대로 수정 요청에 실을 수 있다. */
data class SellerProductDetailResponse(
    /** products.public_id (ULID) */
    val productId: String,
    val categoryId: Long,
    val productName: String,
    val description: String?,
    val regularPrice: Long,
    val discountPrice: Long?,
    val discountStartAt: LocalDateTime?,
    val discountEndAt: LocalDateTime?,
    val thumbnailImageKey: String?,
    val additionalImageKeys: List<String>,
    val detailImageKeys: List<String>,
    val saleStatus: SaleStatus,
    val inspectionStatus: InspectionStatus,
) {
    companion object {
        fun from(result: SellerProductDetailResult): SellerProductDetailResponse =
            SellerProductDetailResponse(
                productId = result.productId,
                categoryId = result.categoryId,
                productName = result.productName,
                description = result.description,
                regularPrice = result.regularPrice,
                discountPrice = result.discountPrice,
                discountStartAt = result.discountStartAt,
                discountEndAt = result.discountEndAt,
                thumbnailImageKey = result.thumbnailImageKey,
                additionalImageKeys = result.additionalImageKeys,
                detailImageKeys = result.detailImageKeys,
                saleStatus = result.saleStatus,
                inspectionStatus = result.inspectionStatus,
            )
    }
}
