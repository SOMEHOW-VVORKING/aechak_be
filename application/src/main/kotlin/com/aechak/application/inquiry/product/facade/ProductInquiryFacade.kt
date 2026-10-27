package com.aechak.application.inquiry.product.facade

import com.aechak.application.inquiry.product.port.view.ProductInquiryView
import com.aechak.application.inquiry.product.service.ProductInquiryService
import com.aechak.application.inquiry.product.support.AuthorNameMask
import com.aechak.application.inquiry.product.usecase.ProductInquiryUseCase
import com.aechak.application.inquiry.product.usecase.SellerProductInquiryUseCase
import com.aechak.application.inquiry.product.usecase.command.AnswerProductInquiryCommand
import com.aechak.application.inquiry.product.usecase.command.AskProductInquiryCommand
import com.aechak.application.inquiry.product.usecase.command.WithdrawProductInquiryCommand
import com.aechak.application.inquiry.product.usecase.query.MyProductInquiryListQuery
import com.aechak.application.inquiry.product.usecase.query.ProductInquiryListQuery
import com.aechak.application.inquiry.product.usecase.query.SellerProductInquirySearchQuery
import com.aechak.application.inquiry.product.usecase.result.AnswerProductInquiryResult
import com.aechak.application.inquiry.product.usecase.result.AskProductInquiryResult
import com.aechak.application.inquiry.product.usecase.result.MyProductInquiryItemResult
import com.aechak.application.inquiry.product.usecase.result.ProductInquiryItemResult
import com.aechak.application.inquiry.product.usecase.result.SellerProductInquiryItemResult
import com.aechak.application.inquiry.product.usecase.result.SellerProductInquiryMetricsResult
import com.aechak.application.inquiry.product.usecase.result.SellerProductInquiryPageResult
import com.aechak.application.product.product.usecase.ProductUseCase
import com.aechak.application.seller.usecase.SellerUseCase
import com.aechak.application.support.OffsetPageResult
import com.aechak.application.user.user.usecase.UserUseCase
import com.aechak.common.error.BusinessException
import com.aechak.domain.inquiry.error.InquiryErrorCode
import com.aechak.domain.seller.seller.enums.SellerStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 상품 문의 유스케이스의 유일한 구현체. 구매자 계약과 셀러 계약을 함께 구현한다 —
 * 두 쪽이 같은 서비스를 쓰므로 Facade를 쪼개면 주입이 복제된다.
 */
@Service
class ProductInquiryFacade(
    private val productInquiryService: ProductInquiryService,
    private val productUseCase: ProductUseCase,
    private val sellerUseCase: SellerUseCase,
    private val userUseCase: UserUseCase,
) : ProductInquiryUseCase,
    SellerProductInquiryUseCase {
    @Transactional
    override fun askProductInquiry(command: AskProductInquiryCommand): AskProductInquiryResult {
        val product = productUseCase.getVisibleProductRef(command.productPublicId)
        return AskProductInquiryResult.from(productInquiryService.ask(command, product, LocalDateTime.now()))
    }

    @Transactional
    override fun withdrawProductInquiry(command: WithdrawProductInquiryCommand) {
        productInquiryService.withdraw(command, LocalDateTime.now())
    }

    @Transactional(readOnly = true)
    override fun getProductInquiries(
        query: ProductInquiryListQuery,
        viewerId: Long,
    ): OffsetPageResult<ProductInquiryItemResult> {
        val productId = productUseCase.getVisibleProductId(query.productPublicId)
        val page = productInquiryService.getProductPage(productId, query)
        val masksByUserId = authorMasks(page.items)
        return page.map { view ->
            ProductInquiryItemResult.from(
                view = view,
                authorMasked = masksByUserId.getValue(view.authorUserId),
                viewerId = viewerId,
            )
        }
    }

    @Transactional(readOnly = true)
    override fun getMyProductInquiries(query: MyProductInquiryListQuery): OffsetPageResult<MyProductInquiryItemResult> {
        val now = LocalDateTime.now()
        return productInquiryService.getMyPage(query).map { MyProductInquiryItemResult.from(it, now) }
    }

    @Transactional(readOnly = true)
    override fun searchMyInquiries(query: SellerProductInquirySearchQuery): SellerProductInquiryPageResult {
        requireInquiryReadableSeller(query.sellerId)
        val now = LocalDateTime.now()
        val page = productInquiryService.getSellerPage(query)
        val masksByUserId = authorMasks(page.items)
        return SellerProductInquiryPageResult(
            page =
                page.map { view ->
                    SellerProductInquiryItemResult.from(
                        view = view,
                        authorMasked = masksByUserId.getValue(view.authorUserId),
                        now = now,
                    )
                },
            metrics = SellerProductInquiryMetricsResult.from(productInquiryService.getSellerMetrics(query.sellerId, now)),
        )
    }

    @Transactional
    override fun answerProductInquiry(command: AnswerProductInquiryCommand): AnswerProductInquiryResult {
        requireActiveSeller(command.sellerId)
        return AnswerProductInquiryResult.from(productInquiryService.answer(command, LocalDateTime.now()))
    }

    /** 작성자 표시명 — 닉네임 조회를 행마다 하지 않고 한 번에 모은다. */
    private fun authorMasks(views: List<ProductInquiryView>): Map<Long, String> {
        val authorIds = views.map { it.authorUserId }
        if (authorIds.isEmpty()) return emptyMap()
        return userUseCase.getAuthors(authorIds).mapValues { (_, author) -> AuthorNameMask.of(author.nickname) }
    }

    private fun requireActiveSeller(sellerId: Long) {
        if (!sellerUseCase.isActiveSeller(sellerId)) {
            throw BusinessException(InquiryErrorCode.PRODUCT_INQUIRY_SELLER_NOT_ACTIVE)
        }
    }

    /** 조회 자격 게이트 — 제재(정지)와 자격 소멸(탈퇴)만 막는다. 휴점·탈퇴신청 셀러는 받은 문의를 본다. */
    private fun requireInquiryReadableSeller(sellerId: Long) {
        if (sellerUseCase.getSellerStatus(sellerId) !in INQUIRY_READABLE_SELLER_STATUSES) {
            throw BusinessException(InquiryErrorCode.PRODUCT_INQUIRY_SELLER_NOT_ACTIVE)
        }
    }

    companion object {
        private val INQUIRY_READABLE_SELLER_STATUSES =
            setOf(SellerStatus.ACTIVE, SellerStatus.PAUSED, SellerStatus.WITHDRAWAL_REQUESTED)
    }
}
