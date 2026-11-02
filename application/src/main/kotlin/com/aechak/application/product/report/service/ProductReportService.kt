package com.aechak.application.product.report.service

import com.aechak.application.product.product.port.ProductCatalogQueryPort
import com.aechak.application.product.report.usecase.command.SubmitProductReportCommand
import com.aechak.common.error.BusinessException
import com.aechak.domain.product.error.ProductErrorCode
import com.aechak.domain.product.product.repository.ProductRepository
import com.aechak.domain.product.report.repository.DuplicateProductReportException
import com.aechak.domain.product.report.repository.ProductReportRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class ProductReportService(
    private val productCatalogQueryPort: ProductCatalogQueryPort,
    private val productRepository: ProductRepository,
    private val productReportRepository: ProductReportRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    fun report(command: SubmitProductReportCommand) {
        // 구매자에게 노출 중인 상품만 신고할 수 있다
        val product =
            productCatalogQueryPort
                .findVisibleIdByPublicId(command.productPublicId)
                ?.let { productRepository.findById(it) }
                ?: throw BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND)
        val report =
            try {
                productReportRepository.save(command.toEntity(product))
            } catch (e: DuplicateProductReportException) {
                throw BusinessException(ProductErrorCode.PRODUCT_ALREADY_REPORTED, e)
            }
        report.registerReceived()
        report.events.forEach { eventPublisher.publishEvent(it) }
        report.clearEvents()
    }
}
