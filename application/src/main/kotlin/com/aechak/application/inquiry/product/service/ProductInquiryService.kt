package com.aechak.application.inquiry.product.service

import com.aechak.application.inquiry.product.port.MyProductInquiryCondition
import com.aechak.application.inquiry.product.port.ProductInquiryPageCondition
import com.aechak.application.inquiry.product.port.ProductInquiryQueryPort
import com.aechak.application.inquiry.product.port.SellerProductInquiryCondition
import com.aechak.application.inquiry.product.port.view.ProductInquiryView
import com.aechak.application.inquiry.product.port.view.SellerProductInquiryMetricsView
import com.aechak.application.inquiry.product.usecase.command.AnswerProductInquiryCommand
import com.aechak.application.inquiry.product.usecase.command.AskProductInquiryCommand
import com.aechak.application.inquiry.product.usecase.command.WithdrawProductInquiryCommand
import com.aechak.application.inquiry.product.usecase.query.MyProductInquiryListQuery
import com.aechak.application.inquiry.product.usecase.query.ProductInquiryListQuery
import com.aechak.application.inquiry.product.usecase.query.SellerProductInquirySearchQuery
import com.aechak.application.product.product.usecase.result.ProductRefResult
import com.aechak.application.support.OffsetPageResult
import com.aechak.common.error.BusinessException
import com.aechak.domain.inquiry.error.InquiryErrorCode
import com.aechak.domain.inquiry.product.ProductInquiry
import com.aechak.domain.inquiry.product.repository.ProductInquiryRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class ProductInquiryService(
    private val productInquiryRepository: ProductInquiryRepository,
    private val productInquiryQueryPort: ProductInquiryQueryPort,
) {
    fun ask(
        command: AskProductInquiryCommand,
        product: ProductRefResult,
        now: LocalDateTime,
    ): ProductInquiry = productInquiryRepository.save(command.toEntity(product, now))

    /** 철회는 작성자 본인만. userId를 끼고 로드하는 시그니처가 소유권 판정 책임을 진다는 계약이다. */
    fun withdraw(
        command: WithdrawProductInquiryCommand,
        now: LocalDateTime,
    ): ProductInquiry {
        val inquiry = load(command.inquiryPublicId)
        if (inquiry.authorUserId != command.authorUserId) {
            throw BusinessException(InquiryErrorCode.PRODUCT_INQUIRY_ACCESS_DENIED)
        }
        inquiry.withdraw(now)
        return inquiry
    }

    /** 답변은 그 상품의 셀러만. 남의 상품 문의에 답하려 하면 403이다. */
    fun answer(
        command: AnswerProductInquiryCommand,
        now: LocalDateTime,
    ): ProductInquiry {
        val inquiry = load(command.inquiryPublicId)
        if (inquiry.sellerId != command.sellerId) {
            throw BusinessException(InquiryErrorCode.PRODUCT_INQUIRY_ANSWER_FORBIDDEN)
        }
        inquiry.writeAnswer(command.sellerId, command.content, now)
        return inquiry
    }

    fun getProductPage(
        productId: Long,
        query: ProductInquiryListQuery,
    ): OffsetPageResult<ProductInquiryView> {
        val condition =
            ProductInquiryPageCondition(
                productId = productId,
                offset = query.page.toLong() * query.size,
                limit = query.size,
            )
        return OffsetPageResult(
            items = productInquiryQueryPort.findProductPage(condition),
            totalCount = productInquiryQueryPort.countProduct(productId),
            page = query.page,
            size = query.size,
        )
    }

    fun getMyPage(query: MyProductInquiryListQuery): OffsetPageResult<ProductInquiryView> {
        val condition =
            MyProductInquiryCondition(
                authorUserId = query.authorUserId,
                status = query.status,
                offset = query.page.toLong() * query.size,
                limit = query.size,
            )
        return OffsetPageResult(
            items = productInquiryQueryPort.findMyPage(condition),
            totalCount = productInquiryQueryPort.countMine(condition),
            page = query.page,
            size = query.size,
        )
    }

    fun getSellerPage(query: SellerProductInquirySearchQuery): OffsetPageResult<ProductInquiryView> {
        val condition =
            SellerProductInquiryCondition(
                sellerId = query.sellerId,
                status = query.status,
                category = query.category,
                keyword = query.keyword?.trim()?.takeIf { it.isNotEmpty() },
                offset = query.page.toLong() * query.size,
                limit = query.size,
            )
        return OffsetPageResult(
            items = productInquiryQueryPort.findSellerPage(condition),
            totalCount = productInquiryQueryPort.countSeller(condition),
            page = query.page,
            size = query.size,
        )
    }

    fun getSellerMetrics(
        sellerId: Long,
        now: LocalDateTime,
    ): SellerProductInquiryMetricsView = productInquiryQueryPort.countSellerMetrics(sellerId, now)

    private fun load(publicId: String): ProductInquiry =
        productInquiryRepository.findByPublicId(publicId)
            ?: throw BusinessException(InquiryErrorCode.PRODUCT_INQUIRY_NOT_FOUND)
}
