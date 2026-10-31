package com.aechak.application.product.draft

import com.aechak.application.product.draft.facade.ProductDraftAutofillFacade
import com.aechak.application.product.draft.port.ProductDraftAutofillPort
import com.aechak.application.product.draft.port.ProductDraftAutofillRequest
import com.aechak.application.product.draft.port.ProductDraftAutofillResult
import com.aechak.application.product.draft.port.ProductDraftAutofillThrottle
import com.aechak.application.product.draft.usecase.command.AutofillProductDraftCommand
import com.aechak.application.seller.usecase.SellerUseCase
import com.aechak.common.error.BusinessException
import com.aechak.domain.product.error.ProductErrorCode
import com.aechak.domain.seller.seller.enums.SellerStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import kotlin.test.assertNotNull

/**
 * 초안 받기의 게이트 순서를 고정한다.
 *
 * 순서가 중요한 자리가 둘이다. ① 어댑터가 없으면 쿨다운을 **태우지 않는다** — 설정이 복구된
 * 뒤에도 셀러가 20초를 기다리게 되면 복구가 복구로 보이지 않는다. ② 쿨다운을 못 얻었으면
 * 포트를 **부르지 않는다** — 그 반대면 쿨다운이 돈을 막지 못한다.
 */
class ProductDraftAutofillFacadeTest {
    private val seller = StubSellerUseCase()
    private val port = RecordingAutofillPort()
    private val throttle = StubThrottle()

    @Test
    fun `활성 셀러가 아니면 거절하고 포트를 부르지 않는다`() {
        seller.status = SellerStatus.SUSPENDED

        val error = assertThrows<BusinessException> { facade().autofill(command()) }

        assertEquals(ProductErrorCode.PRODUCT_SELLER_NOT_ACTIVE, error.errorCode)
        assertNull(port.lastRequest, "자격이 없는 요청에 돈을 쓰지 않는다")
        assertFalse(throttle.called, "거절된 요청은 쿨다운을 소모하지 않는다")
    }

    @Test
    fun `어댑터가 없으면 UNAVAILABLE 이고 쿨다운을 태우지 않는다`() {
        val result = facade(port = null).autofill(command())

        assertEquals(ProductDraftAutofillResult.State.UNAVAILABLE, result.state)
        assertFalse(throttle.called, "설정이 복구된 뒤에 셀러가 쿨다운을 기다리게 하지 않는다")
    }

    @Test
    fun `쿨다운에 걸리면 40402 로 거절하고 포트를 부르지 않는다`() {
        throttle.acquirable = false

        val error = assertThrows<BusinessException> { facade().autofill(command()) }

        assertEquals(ProductErrorCode.PRODUCT_DRAFT_AUTOFILL_COOLDOWN, error.errorCode)
        assertNull(port.lastRequest, "쿨다운이 포트 호출보다 앞이어야 돈을 막는다")
    }

    @Test
    fun `정상이면 요청에 requestId 를 채워 포트에 넘기고 결과를 그대로 돌려준다`() {
        val result = facade().autofill(command())

        assertEquals(ProductDraftAutofillResult.State.OK, result.state)
        assertEquals("초안입니다", result.draft.description)
        assertEquals("FILLED", result.fieldStatus["description"], "필드별 사유가 깎이지 않고 올라간다")
        val sent = assertNotNull(port.lastRequest)
        assertTrue(sent.requestId.isNotBlank(), "요청 상관 키는 서버가 만든다")
        assertEquals("모모의 하루 목줄 M", sent.productName)
        assertEquals(152L, sent.categoryId)
        assertEquals(
            "사이즈",
            sent.lockedFields
                ?.optionGroups
                ?.single()
                ?.name,
        )
        assertEquals(COOLDOWN, throttle.lastTtl, "쿨다운 길이는 설정값을 따른다")
    }

    private fun facade(port: ProductDraftAutofillPort? = this.port) = ProductDraftAutofillFacade(seller, port, throttle, COOLDOWN)

    private fun command() =
        AutofillProductDraftCommand(
            sellerId = 7L,
            productName = "모모의 하루 목줄 M",
            categoryId = 152L,
            lockedDescription = null,
            lockedOptionGroups = listOf(AutofillProductDraftCommand.LockedOptionGroup("사이즈", listOf("S", "M"))),
        )

    private class StubSellerUseCase : SellerUseCase {
        var status: SellerStatus = SellerStatus.ACTIVE

        override fun requireSellerStatus(userId: Long): SellerStatus = status
    }

    private class StubThrottle : ProductDraftAutofillThrottle {
        var acquirable = true
        var called = false
        var lastTtl: Duration? = null

        override fun tryAcquire(
            sellerId: Long,
            ttl: Duration,
        ): Boolean {
            called = true
            lastTtl = ttl
            return acquirable
        }
    }

    private class RecordingAutofillPort : ProductDraftAutofillPort {
        var lastRequest: ProductDraftAutofillRequest? = null

        override fun generate(request: ProductDraftAutofillRequest): ProductDraftAutofillResult {
            lastRequest = request
            return ProductDraftAutofillResult(
                state = ProductDraftAutofillResult.State.OK,
                draft = ProductDraftAutofillResult.Draft(description = "초안입니다"),
                fieldStatus = mapOf("description" to "FILLED"),
                basis = mapOf("description" to "NAME"),
                sellerNotice = null,
            )
        }
    }

    private companion object {
        private val COOLDOWN = Duration.ofSeconds(20)
    }
}
