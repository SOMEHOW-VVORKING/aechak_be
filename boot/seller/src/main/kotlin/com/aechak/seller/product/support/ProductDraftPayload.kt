package com.aechak.seller.product.support

import com.aechak.domain.product.option.OptionGroup
import jakarta.validation.constraints.Size

/**
 * 초안의 중첩 입력. 컬럼으로 펼치지 않고 payload JSON 한 덩어리로 보관한다.
 * 저장과 응답이 같은 모양을 쓰므로 계산 프로퍼티를 두지 않는다 — JSON에 그대로 새어 나간다.
 * 길이·개수 상한은 요청 DTO가 걸고, 옵션 구성 규칙은 정식 등록이 본다.
 */
data class ProductDraftPayload(
    val additionalImageKeys: List<String> = emptyList(),
    val detailImageKeys: List<String> = emptyList(),
    val optionGroups: List<OptionGroupItem> = emptyList(),
    val optionCombinations: List<OptionCombinationItem> = emptyList(),
) {
    data class OptionGroupItem(
        @field:Size(max = OptionGroup.NAME_MAX, message = "옵션 그룹명은 {max}자 이하여야 합니다.")
        val name: String? = null,
        @field:Size(max = GROUP_VALUES_MAX, message = "옵션값은 그룹당 {max}개 이하여야 합니다.")
        val values: List<String> = emptyList(),
    )

    data class OptionCombinationItem(
        @field:Size(max = COMBINATION_VALUES_MAX, message = "조합이 지목하는 옵션값은 {max}개 이하여야 합니다.")
        val optionValues: List<String> = emptyList(),
        val additionalPrice: Long? = null,
        val stockQuantity: Int? = null,
    )

    companion object {
        /** 안쪽 목록도 끊어야 payload 전체 크기가 묶인다 — 조합은 그룹당 하나씩 지목하므로 그룹 상한과 같다 */
        const val GROUP_VALUES_MAX = 50
        const val COMBINATION_VALUES_MAX = 5
    }
}
