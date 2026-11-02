package com.aechak.domain.product.report

import com.aechak.common.error.BusinessException
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.error.ProductErrorCode
import com.aechak.domain.product.product.Product
import com.aechak.domain.product.report.enums.ProductReportReason
import com.aechak.domain.product.report.enums.ProductReportStatus
import com.aechak.domain.product.report.event.ProductReportReceivedEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * 계약 테스트. 상품 신고 팩토리 규칙(본인 상품 차단, 기타 사유의 상세 사유 필수, 상세 사유 정규화)과 접수 이벤트 내용을 고정한다.
 * 깨지면 셀러가 자기 상품을 신고하거나 공백 상세 사유가 그대로 저장되거나 운영팀 메일에 엉뚱한 값이 실린다.
 */
class ProductReportTest {
    private val sellerId = 7L
    private val product =
        Product.register(
            category = Category.create(null, Category.ROOT_DEPTH, "사료", null, 1),
            sellerId = sellerId,
            name = "연어 건사료 2kg",
            description = null,
            representativeImageKey = null,
            regularPrice = 25_000L,
            discountPrice = null,
            discountStartAt = null,
            discountEndAt = null,
        )

    @Test
    fun `본인 상품은 신고할 수 없다`() {
        val e =
            assertFailsWith<BusinessException>("셀러가 자기 상품을 신고하면 거절해야 한다") {
                ProductReport.report(product, sellerId, ProductReportReason.FALSE_AD, null)
            }

        assertEquals(ProductErrorCode.PRODUCT_SELF_REPORT_NOT_ALLOWED, e.errorCode, "본인 상품 신고 코드여야 한다")
    }

    @Test
    fun `기타 사유는 상세 사유가 없거나 공백뿐이면 접수할 수 없다`() {
        listOf(null, "   ").forEach { text ->
            val e =
                assertFailsWith<BusinessException>("상세 사유가 [$text]이면 거절해야 한다") {
                    ProductReport.report(product, 1L, ProductReportReason.OTHER, text)
                }

            assertEquals(ProductErrorCode.PRODUCT_REPORT_REASON_TEXT_REQUIRED, e.errorCode, "상세 사유 필수 코드여야 한다")
        }
    }

    @Test
    fun `상세 사유는 앞뒤 공백을 지우고 공백뿐이면 null로 둔다`() {
        val trimmed = ProductReport.report(product, 1L, ProductReportReason.OTHER, "  광고  ")
        val blank = ProductReport.report(product, 1L, ProductReportReason.FALSE_AD, "   ")

        assertEquals("광고", trimmed.reasonText, "앞뒤 공백을 지워 저장해야 한다")
        assertNull(blank.reasonText, "공백뿐인 상세 사유는 null로 저장해야 한다")
    }

    @Test
    fun `접수하면 RECEIVED 상태이고 접수 이벤트에 상품 publicId와 정규화한 사유를 담는다`() {
        val report = ProductReport.report(product, 1L, ProductReportReason.OTHER, " 가품 의심 ")

        report.registerReceived()

        assertEquals(ProductReportStatus.RECEIVED, report.status, "접수 직후에는 RECEIVED여야 한다")
        val event = assertIs<ProductReportReceivedEvent>(report.events.single(), "접수 이벤트 하나만 등록해야 한다")
        assertEquals(product.publicId, event.productPublicId, "이벤트에 상품 publicId를 담아야 한다")
        assertEquals(ProductReportReason.OTHER, event.reasonCode, "이벤트에 사유 코드를 담아야 한다")
        assertEquals("가품 의심", event.reasonText, "이벤트에 정규화한 상세 사유를 담아야 한다")
    }
}
