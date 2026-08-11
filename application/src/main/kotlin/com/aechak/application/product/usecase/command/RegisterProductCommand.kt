package com.aechak.application.product.usecase.command

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
    data class OptionGroupCommand(
        val name: String,
        val values: List<String>,
    )

    /** 조합을 이루는 옵션값을 이름으로 받음. 그룹과 값이 이 요청에서 함께 만들어져 아직 id가 없기 때문임. */
    data class OptionCombinationCommand(
        val optionValues: List<String>,
        val additionalPrice: Long,
        val stockQuantity: Int,
    )
}
