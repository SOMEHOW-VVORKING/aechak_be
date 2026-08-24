package com.aechak.application.product.product.port.view

/** 상품 옵션 조회의 읽기 모델(활성 그룹/값/조합만, 그룹/값은 sortOrder 순, 조합은 id 순으로 정렬) */
data class ProductOptionsView(
    val optionGroups: List<OptionGroupView>,
    val optionCombinations: List<OptionCombinationView>,
) {
    data class OptionGroupView(
        val optionGroupId: Long,
        val name: String,
        val sortOrder: Int,
        val values: List<OptionValueView>,
    )

    data class OptionValueView(
        val optionValueId: Long,
        val name: String,
        val sortOrder: Int,
    )

    data class OptionCombinationView(
        val optionCombinationId: Long,
        val name: String,
        val additionalPrice: Long,
        val optionValueIds: List<Long>,
        val stockQuantity: Int,
    )
}
