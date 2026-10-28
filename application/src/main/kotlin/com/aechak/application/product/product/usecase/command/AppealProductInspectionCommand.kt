package com.aechak.application.product.product.usecase.command

/**
 * 검수 차단에 대한 셀러 이의.
 *
 * [reason]이 nullable인 이유는 빈 사유를 형식 오류가 아니라 전용 오류 코드로 답하기 위함이다.
 * 셀러에게는 "무엇을 적어야 하는가"가 답이어야 하고, 형식 오류는 그 말을 하지 않는다.
 */
data class AppealProductInspectionCommand(
    val sellerId: Long,
    val productPublicId: String,
    val reason: String?,
)
