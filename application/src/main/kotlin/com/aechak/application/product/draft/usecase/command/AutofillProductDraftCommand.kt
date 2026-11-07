package com.aechak.application.product.draft.usecase.command

/**
 * 초안 받기 입력.
 *
 * FILL 에서는 셀러가 이미 고쳐 둔 값(locked*)을 AI 가 덮지 않고 그대로 지킨다. REFINE 에서는
 * 같은 값이 모델 입력이 되어 다시 쓰인다.
 */
data class AutofillProductDraftCommand(
    val sellerId: Long,
    val productName: String,
    val categoryId: Long?,
    val lockedDescription: String?,
    val lockedOptionGroups: List<LockedOptionGroup>?,
    val mode: ProductDraftAutofillMode = ProductDraftAutofillMode.FILL,
) {
    data class LockedOptionGroup(
        val name: String,
        val values: List<String>,
    )
}
