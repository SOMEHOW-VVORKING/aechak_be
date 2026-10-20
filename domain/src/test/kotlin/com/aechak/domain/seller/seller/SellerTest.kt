package com.aechak.domain.seller.seller

import com.aechak.common.error.BusinessException
import com.aechak.domain.seller.error.SellerErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Seller 스토어 설정 불변식 계약. 상호가 구매자에게 보이는 이름이라 빈 값이 들어가지 않는 것을 고정한다.
 * 깨지면 HTTP 형식 검증을 우회한 입력(공백만 든 상호 등)이 스토어 이름을 비울 수 있다.
 */
class SellerTest {
    @Test
    fun `상호 앞뒤 공백은 떼고 저장한다`() {
        val seller = seller()

        seller.updateStore("  애착스토어  ", null, null, null, null, null, null)

        assertEquals("애착스토어", seller.storeName)
    }

    @Test
    fun `공백만 든 상호는 거절한다`() {
        val seller = seller()

        // NBSP는 Bean Validation의 공백 판정은 통과하지만 Kotlin 정규화로는 사라진다
        listOf("", "   ", " ", "\t\n").forEach { blank ->
            val e = assertFailsWith<BusinessException> { seller.updateStore(blank, null, null, null, null, null, null) }
            assertEquals(SellerErrorCode.INVALID_STORE_SETTINGS, e.errorCode, "거절되지 않은 입력: [$blank]")
        }
        assertEquals("애착상회", seller.storeName, "거절된 입력이 상호를 바꿨다")
    }

    @Test
    fun `정규화한 상호가 컬럼 길이를 넘으면 거절한다`() {
        val seller = seller()

        val e =
            assertFailsWith<BusinessException> {
                seller.updateStore("가".repeat(Seller.MAX_STORE_NAME_LENGTH + 1), null, null, null, null, null, null)
            }

        assertEquals(SellerErrorCode.INVALID_STORE_SETTINGS, e.errorCode)
    }

    @Test
    fun `스토어 주소는 배송 정책의 출고지와 같은 필드다`() {
        val seller = seller()

        seller.updateStore("애착스토어", null, null, null, "06236", "서울 강남구 테헤란로 1", "3층")

        assertEquals("06236", seller.zipCode)
        assertEquals("서울 강남구 테헤란로 1", seller.roadAddress)
        assertEquals("3층", seller.detailAddress)
    }

    private fun seller(): Seller = Seller.open(userId = 1L, storeName = "애착상회", baseShippingFee = 3000L)
}
