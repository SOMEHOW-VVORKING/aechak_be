package com.aechak.seller.product.request

import com.aechak.application.product.draft.usecase.command.SaveProductDraftCommand
import com.aechak.domain.product.draft.ProductDraft
import com.aechak.domain.product.option.OptionValue
import com.aechak.seller.product.support.ProductDraftPayload
import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

/**
 * 상품 임시저장 요청. 전 필드가 선택이다 — 폼을 채우는 중에도 저장이 되어야 해서
 * 필수값·가격·옵션 구성 검증은 정식 등록 요청에만 걸린다.
 * 여기 남은 검증은 저장 단계에서 컬럼 길이를 넘겨 500이 되는 것과 초안이 무한히 커지는 것만 막는다.
 */
data class ProductDraftSaveRequest(
    val categoryId: Long? = null,
    @field:Size(max = ProductDraft.NAME_MAX, message = "상품명은 {max}자 이하여야 합니다.")
    val productName: String? = null,
    val description: String? = null,
    val regularPrice: Long? = null,
    val discountPrice: Long? = null,
    val discountStartAt: LocalDateTime? = null,
    val discountEndAt: LocalDateTime? = null,
    @field:Size(max = ProductDraft.IMAGE_KEY_MAX, message = "이미지 키는 {max}자 이하여야 합니다.")
    val thumbnailImageKey: String? = null,
    @field:Size(max = IMAGE_KEYS_MAX, message = "추가 이미지는 {max}장 이하여야 합니다.")
    val additionalImageKeys: List<String>? = null,
    @field:Size(max = IMAGE_KEYS_MAX, message = "상세 이미지는 {max}장 이하여야 합니다.")
    val detailImageKeys: List<String>? = null,
    @field:Valid
    @field:Size(max = OPTION_GROUPS_MAX, message = "옵션 그룹은 {max}개 이하여야 합니다.")
    val optionGroups: List<ProductDraftPayload.OptionGroupItem>? = null,
    @field:Valid
    @field:Size(max = OPTION_COMBINATIONS_MAX, message = "옵션 조합은 {max}개 이하여야 합니다.")
    val optionCombinations: List<ProductDraftPayload.OptionCombinationItem>? = null,
) {
    @get:AssertTrue(message = "이미지 키는 ${ProductDraft.IMAGE_KEY_MAX}자 이하여야 합니다.")
    val imageKeysWellFormed: Boolean
        get() = (additionalImageKeys.orEmpty() + detailImageKeys.orEmpty()).all { it.length <= ProductDraft.IMAGE_KEY_MAX }

    @get:AssertTrue(message = "옵션값은 ${OptionValue.NAME_MAX}자 이하여야 합니다.")
    val optionValuesWellFormed: Boolean
        get() =
            (optionGroups.orEmpty().flatMap { it.values } + optionCombinations.orEmpty().flatMap { it.optionValues })
                .all { it.length <= OptionValue.NAME_MAX }

    fun toPayload(): ProductDraftPayload =
        ProductDraftPayload(
            additionalImageKeys = additionalImageKeys.orEmpty(),
            detailImageKeys = detailImageKeys.orEmpty(),
            optionGroups = optionGroups.orEmpty(),
            optionCombinations = optionCombinations.orEmpty(),
        )

    fun toCommand(
        sellerId: Long,
        draftId: String?,
        payload: String,
    ): SaveProductDraftCommand =
        SaveProductDraftCommand(
            sellerId = sellerId,
            draftId = draftId,
            categoryId = categoryId,
            productName = productName,
            description = description,
            regularPrice = regularPrice,
            discountPrice = discountPrice,
            discountStartAt = discountStartAt,
            discountEndAt = discountEndAt,
            thumbnailImageKey = thumbnailImageKey,
            payload = payload,
        )

    companion object {
        const val IMAGE_KEYS_MAX = 20
        const val OPTION_GROUPS_MAX = 5
        const val OPTION_COMBINATIONS_MAX = 200
    }
}
