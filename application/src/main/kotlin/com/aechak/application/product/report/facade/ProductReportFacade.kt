package com.aechak.application.product.report.facade

import com.aechak.application.product.report.service.ProductReportService
import com.aechak.application.product.report.usecase.ProductReportUseCase
import com.aechak.application.product.report.usecase.command.SubmitProductReportCommand
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProductReportFacade(
    private val productReportService: ProductReportService,
) : ProductReportUseCase {
    @Transactional
    override fun submitProductReport(command: SubmitProductReportCommand) = productReportService.report(command)
}
