package com.aechak.infra.persistence.product.report

import com.aechak.domain.product.report.ProductReport
import com.aechak.domain.product.report.repository.DuplicateProductReportException
import com.aechak.domain.product.report.repository.ProductReportRepository
import com.aechak.infra.persistence.support.isUniqueViolationOf
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface ProductReportJpaRepository : JpaRepository<ProductReport, Long>

@Repository
class ProductReportRepositoryAdapter(
    private val jpaRepository: ProductReportJpaRepository,
) : ProductReportRepository {
    override fun save(report: ProductReport): ProductReport =
        try {
            jpaRepository.saveAndFlush(report)
        } catch (e: DataIntegrityViolationException) {
            if (e.isUniqueViolationOf(ProductReport.UK_PRODUCT_ID_REPORTER_ID)) {
                throw DuplicateProductReportException(e)
            }
            throw e
        }
}
