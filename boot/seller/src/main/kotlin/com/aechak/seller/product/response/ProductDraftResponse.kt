package com.aechak.seller.product.response

import com.aechak.application.product.draft.usecase.result.ProductDraftResult
import com.aechak.seller.product.support.ProductDraftPayload
import java.time.LocalDateTime

/** 등록 폼 프리필 응답 — 이미지 키는 승격 전 tmp 키라 그대로 정식 등록 요청에 실을 수 있다. */
data class ProductDraftResponse(
    val draftId: String,
    val savedAt: LocalDateTime,
    val categoryId: Long?,
    val productName: String?,
    val description: String?,
    val regularPrice: Long?,
    val discountPrice: Long?,
    val discountStartAt: LocalDateTime?,
    val discountEndAt: LocalDateTime?,
    val thumbnailImageKey: String?,
    val additionalImageKeys: List<String>,
    val detailImageKeys: List<String>,
    val optionGroups: List<ProductDraftPayload.OptionGroupItem>,
    val optionCombinations: List<ProductDraftPayload.OptionCombinationItem>,
) {
    companion object {
        fun from(
            result: ProductDraftResult,
            payload: ProductDraftPayload,
        ): ProductDraftResponse =
            ProductDraftResponse(
                draftId = result.draftId,
                savedAt = result.savedAt,
                categoryId = result.categoryId,
                productName = result.productName,
                description = result.description,
                regularPrice = result.regularPrice,
                discountPrice = result.discountPrice,
                discountStartAt = result.discountStartAt,
                discountEndAt = result.discountEndAt,
                thumbnailImageKey = result.thumbnailImageKey,
                additionalImageKeys = payload.additionalImageKeys,
                detailImageKeys = payload.detailImageKeys,
                optionGroups = payload.optionGroups,
                optionCombinations = payload.optionCombinations,
            )
    }
}
