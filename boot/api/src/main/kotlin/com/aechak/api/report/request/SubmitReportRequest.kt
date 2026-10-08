package com.aechak.api.report.request

import com.aechak.application.product.report.usecase.command.SubmitProductReportCommand
import com.aechak.application.review.report.usecase.command.SubmitReviewReportCommand
import com.aechak.application.user.report.usecase.command.SubmitUserReportCommand
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.product.report.ProductReport
import com.aechak.domain.product.report.enums.ProductReportReason
import com.aechak.domain.review.report.ReviewReport
import com.aechak.domain.review.report.enums.ReviewReportReason
import com.aechak.domain.user.report.UserReport
import com.aechak.domain.user.report.enums.ReportReasonCode
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

/** 신고 접수 요청. targetId와 reasonCode는 대상 종류에 따라 해석한다 */
data class SubmitReportRequest(
    @field:NotNull(message = "신고 대상 종류는 필수입니다.")
    val targetType: ReportTargetType?,
    @field:NotBlank(message = "신고 대상 id는 필수입니다.")
    @field:Schema(description = "PRODUCT는 상품 productId(ULID), REVIEW는 리뷰 id, USER는 신고할 사용자가 쓴 리뷰 id")
    val targetId: String,
    @field:NotBlank(message = "신고 사유는 필수입니다.")
    @field:Schema(
        description =
            "PRODUCT는 FALSE_AD, INFO_ERROR, ILLEGAL, INAPPROPRIATE, OTHER. " +
                "REVIEW와 USER는 FRAUD, ABUSE, SPAM, INAPPROPRIATE, OTHER",
    )
    val reasonCode: String,
    val reasonText: String? = null,
) {
    fun toProductCommand(userId: Long): SubmitProductReportCommand =
        SubmitProductReportCommand(
            reporterId = userId,
            productPublicId = targetId,
            reasonCode = parseEnum<ProductReportReason>("reasonCode", reasonCode),
            reasonText = reasonTextWithin(ProductReport.REASON_TEXT_MAX),
        )

    fun toReviewCommand(userId: Long): SubmitReviewReportCommand =
        SubmitReviewReportCommand(
            reporterId = userId,
            reviewId = parseReviewId(),
            reasonCode = parseEnum<ReviewReportReason>("reasonCode", reasonCode),
            reasonText = reasonTextWithin(ReviewReport.REASON_TEXT_MAX),
        )

    fun toUserCommand(userId: Long): SubmitUserReportCommand =
        SubmitUserReportCommand(
            reporterId = userId,
            reviewId = parseReviewId(),
            reasonCode = parseEnum<ReportReasonCode>("reasonCode", reasonCode),
            reasonText = reasonTextWithin(UserReport.REASON_TEXT_MAX),
        )

    private fun parseReviewId(): Long =
        targetId.toLongOrNull()?.takeIf { it > 0 }
            ?: throw BusinessException(CommonErrorCode.INVALID_REQUEST, detail = "targetId: 리뷰 id 형식이 올바르지 않습니다.")

    private fun reasonTextWithin(max: Int): String? {
        if ((reasonText?.length ?: 0) > max) {
            throw BusinessException(CommonErrorCode.INVALID_REQUEST, detail = "reasonText: 상세 사유는 ${max}자를 넘을 수 없습니다.")
        }
        return reasonText
    }

    private inline fun <reified E : Enum<E>> parseEnum(
        field: String,
        value: String,
    ): E =
        try {
            enumValueOf<E>(value)
        } catch (e: IllegalArgumentException) {
            throw BusinessException(CommonErrorCode.INVALID_REQUEST, detail = "$field: 지원하지 않는 값입니다.")
        }
}

/** 신고 대상 종류 */
enum class ReportTargetType {
    PRODUCT,
    REVIEW,
    USER,
}
