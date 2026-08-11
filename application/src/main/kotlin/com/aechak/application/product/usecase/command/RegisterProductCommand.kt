package com.aechak.application.product.usecase.command

import com.aechak.domain.product.category.Category
import com.aechak.domain.product.option.ProductOptions
import com.aechak.domain.product.product.Product
import java.time.LocalDateTime

data class RegisterProductCommand(
    val sellerId: Long,
    val categoryId: Long,
    val productName: String,
    val description: String?,
    val regularPrice: Long,
    val discountPrice: Long?,
    val discountStartAt: LocalDateTime?,
    val discountEndAt: LocalDateTime?,
    val thumbnailImageKey: String,
    val additionalImageKeys: List<String>,
    val detailImageKeys: List<String>,
    val optionGroups: List<OptionGroupCommand>,
    val optionCombinations: List<OptionCombinationCommand>,
) {
    fun toEntity(category: Category): Product =
        Product.register(
            category = category,
            sellerId = sellerId,
            name = productName,
            description = description,
            representativeImageKey = thumbnailImageKey,
            regularPrice = regularPrice,
            discountPrice = discountPrice,
            discountStartAt = discountStartAt,
            discountEndAt = discountEndAt,
            additionalImageKeys = additionalImageKeys,
            detailImageKeys = detailImageKeys,
        )

    fun toProductOptions(): ProductOptions =
        ProductOptions.of(
            groups = optionGroups.map { ProductOptions.GroupSpec(it.name, it.values) },
            combinations =
                optionCombinations.map {
                    ProductOptions.CombinationSpec(it.optionValues, it.additionalPrice, it.stockQuantity)
                },
        )

    data class OptionGroupCommand(
        val name: String,
        val values: List<String>,
    )

    data class OptionCombinationCommand(
        val optionValues: List<String>,
        val additionalPrice: Long,
        val stockQuantity: Int,
    )
}
