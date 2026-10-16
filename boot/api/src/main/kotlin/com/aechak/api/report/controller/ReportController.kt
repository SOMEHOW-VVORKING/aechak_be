package com.aechak.api.report.controller

import com.aechak.api.report.request.ReportTargetType
import com.aechak.api.report.request.SubmitReportRequest
import com.aechak.application.product.report.usecase.ProductReportUseCase
import com.aechak.application.review.report.usecase.ReviewReportUseCase
import com.aechak.application.user.report.usecase.UserReportUseCase
import com.aechak.websecurity.authentication.AuthPrincipal
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/reports")
class ReportController(
    private val productReportUseCase: ProductReportUseCase,
    private val reviewReportUseCase: ReviewReportUseCase,
    private val userReportUseCase: UserReportUseCase,
) {
    @PostMapping
    fun submitReport(
        @Valid @RequestBody request: SubmitReportRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<Void> {
        when (request.targetType!!) {
            ReportTargetType.PRODUCT -> productReportUseCase.submitProductReport(request.toProductCommand(principal.userId))
            ReportTargetType.REVIEW -> reviewReportUseCase.submitReviewReport(request.toReviewCommand(principal.userId))
            ReportTargetType.USER -> userReportUseCase.submitUserReport(request.toUserCommand(principal.userId))
        }
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }
}
