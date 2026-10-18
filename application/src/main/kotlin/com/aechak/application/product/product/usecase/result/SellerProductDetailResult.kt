package com.aechak.application.product.product.usecase.result

import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.ProductImage
import com.aechak.domain.product.product.enums.InspectionStatus
import com.aechak.domain.product.product.enums.ProductImageType
import com.aechak.domain.product.product.enums.SaleStatus
import java.time.LocalDateTime

/** 셀러 상품 단건 — 수정 폼 프리필용. 이미지는 저장 키 그대로 담는다. */
data class SellerProductDetailResult(
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
        /** 이미지는 currentImages만 쓴다. 닫힌 키를 실어 보내면 그 응답으로 수정할 때 소유 증명에서 걸린다. */
        fun from(product: Product): SellerProductDetailResult {
            val current = product.currentImages
            return SellerProductDetailResult(
                productId = product.publicId,
                categoryId = product.category.id,
                productName = product.name,
                description = product.description,
                regularPrice = product.regularPrice,
                discountPrice = product.discountPrice,
                discountStartAt = product.discountStartAt,
                discountEndAt = product.discountEndAt,
                thumbnailImageKey = current.keysOf(ProductImageType.REPRESENTATIVE).firstOrNull(),
                additionalImageKeys = current.keysOf(ProductImageType.PRODUCT),
                detailImageKeys = current.keysOf(ProductImageType.DETAIL),
                saleStatus = product.saleStatus,
                inspectionStatus = product.inspectionStatus,
            )
        }

        /** 한 종류의 키를 sortOrder 오름차순으로 뽑는다. */
        private fun List<ProductImage>.keysOf(imageType: ProductImageType): List<String> =
            filter { it.imageType == imageType }.sortedBy { it.sortOrder }.map { it.storageKey }
    }
}
