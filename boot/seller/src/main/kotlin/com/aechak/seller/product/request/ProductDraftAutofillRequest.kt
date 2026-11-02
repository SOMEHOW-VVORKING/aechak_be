package com.aechak.seller.product.request

import com.aechak.application.product.draft.usecase.command.AutofillProductDraftCommand
import com.aechak.domain.product.draft.ProductDraft
import com.aechak.seller.product.support.ProductDraftPayload
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * 폼 초안 받기 요청. 상품명만 필수다.
 *
 * 상한은 기존 임시저장 DTO 상수를 재사용한다 — AI 가 거는 수와 같아서, 우리가 먼저 보면 셀러가
 * AI 의 422(빈 초안) 대신 우리의 400 과 메시지를 받는다.
 */
data class ProductDraftAutofillRequest(
    @field:NotBlank(message = "상품명을 입력해 주세요.") // AI 는 빈 상품명에 422 를 돌려준다
    @field:Size(max = ProductDraft.NAME_MAX, message = "상품명은 {max}자 이하여야 합니다.")
    val productName: String? = null,
    /** depth 1·2·3 아무거나. AI 가 상품명에서 품목을 못 집으면 이것으로 보완한다 */
    val categoryId: Long? = null,
    @field:Valid
    val lockedFields: LockedFields? = null,
) {
    /** 셀러가 이미 고쳐 둔 값. AI 가 덮지 않고 그대로 지킨다(재생성에서 쓴다). */
    data class LockedFields(
        @field:Size(max = ProductDraft.DESCRIPTION_MAX, message = "상세 설명은 {max}자 이하여야 합니다.")
        val description: String? = null,
        @field:Valid
        @field:Size(max = ProductDraftSaveRequest.OPTION_GROUPS_MAX, message = "옵션 그룹은 {max}개 이하여야 합니다.")
        val optionGroups: List<ProductDraftPayload.OptionGroupItem>? = null,
    )

    fun toCommand(sellerId: Long): AutofillProductDraftCommand =
        AutofillProductDraftCommand(
            sellerId = sellerId,
            // @NotBlank 를 통과한 뒤라 비어 있지 않다
            productName = productName.orEmpty(),
            categoryId = categoryId,
            lockedDescription = lockedFields?.description,
            lockedOptionGroups =
                lockedFields?.optionGroups?.map {
                    // 임시저장은 이름 없는 그룹을 허용하지만 AI 쪽 name 은 필수다
                    AutofillProductDraftCommand.LockedOptionGroup(it.name.orEmpty(), it.values)
                },
        )
}
