package com.aechak.domain.product.product

import com.aechak.common.error.BusinessException
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.error.ProductErrorCode
import com.aechak.domain.product.product.enums.SaleStatus
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 단위 테스트. 검수에서 걸린 상품을 셀러가 판매중으로 올리지 못한다는 불변식을 고정한다.
 *
 * 깨지면 걸린 상품을 셀러가 판매중지로 내렸다가 다시 올리는 것이 그대로 통한다. 노출 게이트가 꺼진
 * 환경에서는 검수 상태가 승인으로 남으므로, 관리자 검토 대기 여부를 함께 보지 않으면 가드가 서지 않는다.
 */
class ProductInspectionSaleGuardTest {
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
    fun `검수에서 반려된 상품은 판매중으로 올릴 수 없다`() {
        val product = product()
        product.changeSaleStatusBySeller(SaleStatus.SUSPENDED, awaitingInspectionReview = false)
        product.rejectByInspection()

        val thrown =
            assertThrownBusinessException { product.changeSaleStatusBySeller(SaleStatus.ON_SALE, awaitingInspectionReview = false) }

        assertEquals(ProductErrorCode.PRODUCT_SALE_BLOCKED_BY_INSPECTION, thrown.errorCode)
        assertEquals(SaleStatus.SUSPENDED, product.saleStatus, "거부된 요청이 상태를 건드리면 안 된다")
    }

    @Test
    fun `검수 상태가 승인이어도 관리자 검토를 기다리는 상품은 판매중으로 올릴 수 없다`() {
        val product = product()
        product.changeSaleStatusBySeller(SaleStatus.SUSPENDED, awaitingInspectionReview = false)

        val thrown =
            assertThrownBusinessException {
                product.changeSaleStatusBySeller(SaleStatus.ON_SALE, awaitingInspectionReview = true)
            }

        assertEquals(ProductErrorCode.PRODUCT_SALE_BLOCKED_BY_INSPECTION, thrown.errorCode)
        assertEquals(SaleStatus.SUSPENDED, product.saleStatus, "거부된 요청이 상태를 건드리면 안 된다")
    }

    @Test
    fun `검수에서 걸린 상품도 판매중지로 내릴 수 있다`() {
        val rejected = product()
        rejected.rejectByInspection()
        val awaitingReview = product()

        rejected.changeSaleStatusBySeller(SaleStatus.SUSPENDED, awaitingInspectionReview = false)
        awaitingReview.changeSaleStatusBySeller(SaleStatus.SUSPENDED, awaitingInspectionReview = true)

        assertEquals(SaleStatus.SUSPENDED, rejected.saleStatus, "내리는 방향까지 막으면 셀러가 스스로 거둘 수 없다")
        assertEquals(SaleStatus.SUSPENDED, awaitingReview.saleStatus)
    }

    @Test
    fun `재검수가 통과하면 다시 판매중으로 올릴 수 있다`() {
        val product = product()
        product.rejectByInspection()
        product.changeSaleStatusBySeller(SaleStatus.SUSPENDED, awaitingInspectionReview = false)
        product.approveByInspection()

        product.changeSaleStatusBySeller(SaleStatus.ON_SALE, awaitingInspectionReview = false)

        assertEquals(SaleStatus.ON_SALE, product.saleStatus)
    }

    @Test
    fun `운영자가 막은 상품은 검수에 걸려 있어도 운영자 차단으로 거부한다`() {
        val product = product()
        product.rejectByInspection()
        product.blockSaleByAdmin(1L, "사유")

        val thrown =
            assertThrownBusinessException { product.changeSaleStatusBySeller(SaleStatus.ON_SALE, awaitingInspectionReview = false) }

        assertEquals(ProductErrorCode.PRODUCT_SALE_BLOCKED_BY_ADMIN, thrown.errorCode)
    }

    private fun assertThrownBusinessException(block: () -> Unit): BusinessException =
        try {
            block()
            error("BusinessException이 던져지지 않았다")
        } catch (e: BusinessException) {
            e
        }
}
