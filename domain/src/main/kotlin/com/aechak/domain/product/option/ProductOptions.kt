package com.aechak.domain.product.option

import com.aechak.common.error.BusinessException
import com.aechak.domain.product.error.ProductErrorCode

/**
 * 상품 하나가 가질 옵션 구성. 그룹 목록과 조합 목록을 함께 봐야 판정되는 규칙을 여기서 지킨다.
 * 조합 하나만 보는 OptionCombination.create는 그 값이 어느 그룹 소속인지 알 수 없다.
 */
class ProductOptions private constructor(
    val groups: List<GroupSpec>,
    val combinations: List<CombinationSpec>,
) {
    data class GroupSpec(
        val name: String,
        val valueNames: List<String>,
    )

    data class CombinationSpec(
        val valueNames: List<String>,
        val additionalPrice: Long,
        val stockQuantity: Int,
    )

    companion object {
        fun of(
            groups: List<GroupSpec>,
            combinations: List<CombinationSpec>,
        ): ProductOptions {
            if (combinations.isEmpty()) {
                reject("판매와 재고 단위가 되는 옵션 조합이 최소 하나 필요합니다.")
            }
            val groupNames = groups.map { it.name }
            if (groupNames.distinct().size != groupNames.size) {
                reject("옵션 그룹명은 중복될 수 없습니다.")
            }
            // 조합이 옵션값을 이름으로 지목하므로 그룹이 달라도 같은 이름이 있으면 어느 값인지 가릴 수 없다
            val valueNames = groups.flatMap { it.valueNames }
            if (valueNames.distinct().size != valueNames.size) {
                reject("옵션값 이름은 그룹이 달라도 중복될 수 없습니다.")
            }
            combinations.forEach { validateComposition(it, groups) }
            val keys = combinations.map { it.valueNames.toSet() }
            if (keys.distinct().size != keys.size) {
                reject("같은 옵션값으로 이뤄진 조합을 두 번 보낼 수 없습니다.")
            }
            return ProductOptions(groups, combinations)
        }

        private fun validateComposition(
            combination: CombinationSpec,
            groups: List<GroupSpec>,
        ) {
            val groupIndexes = combination.valueNames.map { name -> groups.indexOfFirst { name in it.valueNames } }
            if (groupIndexes.any { it < 0 }) {
                reject("옵션 그룹에 없는 옵션값을 지목했습니다.")
            }
            // 한 그룹에서 둘을 고르면 화면에서 선택될 수 없는 조합이 되고, 덜 고르면 그 조합을 찾지 못한다
            if (groupIndexes.distinct().size != groups.size) {
                reject("옵션 조합은 그룹마다 옵션값을 하나씩 지목해야 합니다.")
            }
        }

        private fun reject(detail: String): Nothing = throw BusinessException(ProductErrorCode.INVALID_PRODUCT_OPTIONS, detail = detail)
    }
}
