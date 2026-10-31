package com.aechak.application.product.draft.usecase.command

/** 초안 받기 입력. 셀러가 이미 고쳐 둔 값(locked*)은 AI 가 덮지 않고 그대로 지킨다. */
data class AutofillProductDraftCommand(
    val sellerId: Long,
    val productName: String,
    val categoryId: Long?,
    val lockedDescription: String?,
    val lockedOptionGroups: List<LockedOptionGroup>?,
) {
    data class LockedOptionGroup(
        val name: String,
        val values: List<String>,
    )
}
