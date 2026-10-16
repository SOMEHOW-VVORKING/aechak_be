package com.aechak.application.product.report.usecase

import com.aechak.application.product.report.usecase.command.SubmitProductReportCommand

interface ProductReportUseCase {
    /** 상품 신고 접수 */
    fun submitProductReport(command: SubmitProductReportCommand)
}
