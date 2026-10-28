package com.aechak.application.product.product.usecase.result

/** 셀러에게 보여 주는 검수 진행 단계 */
enum class InspectionProgressStep {
    REQUESTED,
    AI_REVIEWING,
    ADMIN_REVIEWING,
    APPROVED,
    REJECTED,
}
