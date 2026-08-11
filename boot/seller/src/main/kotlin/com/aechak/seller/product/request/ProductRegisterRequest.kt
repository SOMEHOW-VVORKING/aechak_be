package com.aechak.seller.product.request

import com.aechak.application.product.usecase.command.RegisterProductCommand
import com.aechak.domain.product.option.OptionGroup
import com.aechak.domain.product.option.OptionValue
import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.ProductImage
import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

data class ProductRegisterRequest(
    val categoryId: Long,
    @field:NotBlank(message = "상품명은 필수입니다.")
    @field:Size(max = Product.NAME_MAX, message = "상품명은 {max}자 이하여야 합니다.")
    val productName: String,
    val description: String? = null,
    val regularPrice: Long,
    val discountPrice: Long? = null,
    val discountStartAt: LocalDateTime? = null,
    val discountEndAt: LocalDateTime? = null,
    @field:NotBlank(message = "대표 이미지 키는 필수입니다.")
    @field:Size(max = ProductImage.STORAGE_KEY_MAX, message = "이미지 키는 {max}자 이하여야 합니다.")
    val thumbnailImageKey: String,
    val additionalImageKeys: List<String>? = null,
    val detailImageKeys: List<String>? = null,
    @field:Valid
    val optionGroups: List<OptionGroupItem>? = null,
    @field:Valid
    val optionCombinations: List<OptionCombinationItem>,
) {
    /** 통과시키면 컬럼 길이를 넘겨 저장 단계에서 500이 됨. */
    @get:AssertTrue(message = "이미지 키는 비울 수 없고 ${ProductImage.STORAGE_KEY_MAX}자 이하여야 합니다.")
    val imageKeysWellFormed: Boolean
        get() =
            (additionalImageKeys.orEmpty() + detailImageKeys.orEmpty())
                .all { it.isNotBlank() && it.length <= ProductImage.STORAGE_KEY_MAX }

    data class OptionGroupItem(
        @field:NotBlank(message = "옵션 그룹명은 필수입니다.")
        @field:Size(max = OptionGroup.NAME_MAX, message = "옵션 그룹명은 {max}자 이하여야 합니다.")
        val name: String,
        @field:NotEmpty(message = "옵션값은 최소 하나가 필요합니다.")
        val values: List<String>,
    ) {
        @get:AssertTrue(message = "옵션값은 비울 수 없고 ${OptionValue.NAME_MAX}자 이하여야 합니다.")
        val valuesWellFormed: Boolean
            get() = values.all { it.isNotBlank() && it.length <= OptionValue.NAME_MAX }
    }

    data class OptionCombinationItem(
        val optionValues: List<String>,
        val additionalPrice: Long,
        val stockQuantity: Int,
    )

    fun toCommand(sellerId: Long): RegisterProductCommand =
        RegisterProductCommand(
            sellerId = sellerId,
            categoryId = categoryId,
            productName = productName,
            description = description,
            regularPrice = regularPrice,
            discountPrice = discountPrice,
            discountStartAt = discountStartAt,
            discountEndAt = discountEndAt,
            thumbnailImageKey = thumbnailImageKey,
            additionalImageKeys = additionalImageKeys ?: emptyList(),
            detailImageKeys = detailImageKeys ?: emptyList(),
            optionGroups = optionGroups.orEmpty().map { RegisterProductCommand.OptionGroupCommand(it.name, it.values) },
            optionCombinations =
                optionCombinations.map {
                    RegisterProductCommand.OptionCombinationCommand(it.optionValues, it.additionalPrice, it.stockQuantity)
                },
        )
}
