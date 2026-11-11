package com.aechak.domain.product.product

import com.aechak.common.error.BusinessException
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.error.ProductErrorCode
import com.aechak.domain.product.product.enums.SaleStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 단위 테스트. 운영자 판매중지가 셀러 경로에 뒤집히지 않는다는 불변식을 고정한다.
 *
 * 깨지면 조치가 무의미해진다 — SUSPENDED는 셀러가 지정할 수 있는 값이라, 막음 표시를 보지 않는
 * 셀러 전이가 하나라도 남아 있으면 운영자가 막은 상품을 셀러가 다음 요청 한 번으로 판매중으로 돌린다.
 */
class ProductAdminSaleBlockTest {
    private val category = Category.create(null, Category.ROOT_DEPTH, "사료", null, 1)

    private fun product(): Product =
        Product.register(
            category = category,
            sellerId = 1L,
            name = "연어 건사료 2kg",
            description = "설명",
            representativeImageKey = "products/thumb.png",
            regularPrice = 25_000L,
            discountPrice = null,
            discountStartAt = null,
            discountEndAt = null,
        )

    @Test
    fun `운영자가 막으면 판매중지가 되고 막음 표시가 남는다`() {
        val product = product()

        product.blockSaleByAdmin(adminId = 7L, reason = "원산지 표기 허위")

        assertEquals(SaleStatus.SUSPENDED, product.saleStatus, "노출과 구매에서 빠지는 값으로 옮겨야 한다")
        assertTrue(product.adminSaleBlocked, "막음 표시가 켜져야 한다")
        assertEquals("원산지 표기 허위", product.adminSaleBlockReason)
        assertEquals(7L, product.adminSaleBlockedByAdminId)
        assertNotNull(product.adminSaleBlockedAt)
        assertEquals(SaleStatus.ON_SALE, product.adminSaleBlockPreviousStatus, "막기 직전 상태를 보관해야 한다")
    }

    @Test
    fun `이미 막힌 상품을 다시 막으면 40012로 거부한다`() {
        val product = product()
        product.blockSaleByAdmin(1L, "1차 사유")

        val thrown = assertThrownBusinessException { product.blockSaleByAdmin(2L, "2차 사유") }

        assertEquals(ProductErrorCode.PRODUCT_SALE_ALREADY_BLOCKED_BY_ADMIN, thrown.errorCode)
        assertEquals("1차 사유", product.adminSaleBlockReason, "거부된 조치가 사유를 덮으면 처음 막은 근거가 사라진다")
        assertEquals(1L, product.adminSaleBlockedByAdminId, "거부된 조치가 행위자를 덮으면 안 된다")
    }

    @Test
    fun `재개하면 막기 직전 상태로 돌아가고 표시가 지워진다`() {
        val product = product()
        product.blockSaleByAdmin(1L, "사유")

        product.unblockSaleByAdmin()

        assertEquals(SaleStatus.ON_SALE, product.saleStatus)
        assertFalse(product.adminSaleBlocked)
        assertNull(product.adminSaleBlockReason)
        assertNull(product.adminSaleBlockedAt)
        assertNull(product.adminSaleBlockedByAdminId)
        assertNull(product.adminSaleBlockPreviousStatus)
    }

    @Test
    fun `셀러가 스스로 내려 둔 판매중지는 재개가 판매중으로 올리지 않는다`() {
        val product = product()
        product.changeSaleStatusBySeller(SaleStatus.SUSPENDED, awaitingInspectionReview = false)
        product.blockSaleByAdmin(1L, "사유")

        product.unblockSaleByAdmin()

        assertEquals(
            SaleStatus.SUSPENDED,
            product.saleStatus,
            "운영자 조치를 푸는 것이지 셀러 의사를 뒤집는 것이 아니다",
        )
    }

    @Test
    fun `품절이던 상품을 막고 재개하면 품절로 돌아간다`() {
        val product = product()
        product.syncSaleStatusWithStock(hasActiveStock = false)
        product.blockSaleByAdmin(1L, "사유")

        product.unblockSaleByAdmin()

        assertEquals(SaleStatus.OUT_OF_STOCK, product.saleStatus, "품절은 재고 파생값이라 되돌린 뒤 동기화가 맞춘다")
    }

    @Test
    fun `막혀 있지 않은 상품을 재개하면 40009로 거부한다`() {
        val product = product()

        val thrown = assertThrownBusinessException { product.unblockSaleByAdmin() }

        assertEquals(ProductErrorCode.PRODUCT_SALE_NOT_BLOCKED_BY_ADMIN, thrown.errorCode)
        assertEquals(SaleStatus.ON_SALE, product.saleStatus, "거부된 요청이 상태를 건드리면 안 된다")
    }

    @Test
    fun `막힌 상품은 셀러가 판매중으로 되돌릴 수 없다`() {
        val product = product()
        product.blockSaleByAdmin(1L, "사유")

        val thrown =
            assertThrownBusinessException { product.changeSaleStatusBySeller(SaleStatus.ON_SALE, awaitingInspectionReview = false) }

        assertEquals(ProductErrorCode.PRODUCT_SALE_BLOCKED_BY_ADMIN, thrown.errorCode)
        assertEquals(SaleStatus.SUSPENDED, product.saleStatus, "조치가 셀러 요청 한 번에 뒤집히면 안 된다")
    }

    @Test
    fun `막힌 상품은 셀러가 판매중지로 다시 지정하는 것도 거부한다`() {
        val product = product()
        product.blockSaleByAdmin(1L, "사유")

        val thrown =
            assertThrownBusinessException { product.changeSaleStatusBySeller(SaleStatus.SUSPENDED, awaitingInspectionReview = false) }

        assertEquals(
            ProductErrorCode.PRODUCT_SALE_BLOCKED_BY_ADMIN,
            thrown.errorCode,
            "같은 결과로 보이지만 통과시키면 직전 상태 보관이 셀러 값으로 덮인다",
        )
        assertEquals(SaleStatus.ON_SALE, product.adminSaleBlockPreviousStatus, "보관한 직전 상태가 바뀌면 안 된다")
    }

    @Test
    fun `재개한 뒤에는 셀러가 다시 판매 상태를 바꿀 수 있다`() {
        val product = product()
        product.blockSaleByAdmin(1L, "사유")
        product.unblockSaleByAdmin()

        product.changeSaleStatusBySeller(SaleStatus.SUSPENDED, awaitingInspectionReview = false)

        assertEquals(SaleStatus.SUSPENDED, product.saleStatus)
    }

    @Test
    fun `막혀 있는 동안 재고 동기화는 판매중지를 건드리지 않는다`() {
        val product = product()
        product.blockSaleByAdmin(1L, "사유")

        product.syncSaleStatusWithStock(hasActiveStock = true)

        assertEquals(SaleStatus.SUSPENDED, product.saleStatus, "재입고 리스너가 막음을 풀면 안 된다")
    }

    @Test
    fun `막는 것은 검수 상태를 건드리지 않는다`() {
        val product = product()
        val before = product.inspectionStatus

        product.blockSaleByAdmin(1L, "사유")

        assertEquals(before, product.inspectionStatus, "검수 판정과 판매 차단은 다른 축이다")
    }

    private fun assertThrownBusinessException(block: () -> Unit): BusinessException =
        try {
            block()
            error("BusinessException이 던져지지 않았다")
        } catch (e: BusinessException) {
            e
        }
}
