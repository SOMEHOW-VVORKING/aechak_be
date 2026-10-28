package com.aechak.domain.order.claim

import com.aechak.domain.order.claim.enums.ClaimReasonCode
import com.aechak.domain.order.claim.enums.ClaimType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 확정된 어휘를 그대로 못박는다. 코드와 문구가 서버·셀러센터·구매자 화면이 함께 쓰는 계약이라
 * 조용히 바뀌면 셀러가 읽던 사유가 다른 뜻이 된다. 선택지 순서도 화면 순서라 함께 고정한다.
 */
class ClaimReasonCodeTest {
    @Test
    fun `취소가 받는 사유와 순서`() {
        assertEquals(
            listOf(
                ClaimReasonCode.CHANGE_OF_MIND,
                ClaimReasonCode.WRONG_OPTION,
                ClaimReasonCode.PRICE_MISMATCH,
                ClaimReasonCode.DELIVERY_DELAY,
                ClaimReasonCode.OTHER,
            ),
            ClaimReasonCode.selectableFor(ClaimType.CANCEL),
        )
    }

    @Test
    fun `반품이 받는 사유와 순서`() {
        assertEquals(
            listOf(
                ClaimReasonCode.CHANGE_OF_MIND,
                ClaimReasonCode.DEFECTIVE,
                ClaimReasonCode.WRONG_ITEM,
                ClaimReasonCode.NOT_AS_DESCRIBED,
                ClaimReasonCode.OTHER,
            ),
            ClaimReasonCode.selectableFor(ClaimType.RETURN),
        )
    }

    @Test
    fun `교환이 받는 사유와 순서`() {
        assertEquals(
            listOf(
                ClaimReasonCode.DEFECTIVE,
                ClaimReasonCode.WRONG_ITEM,
                ClaimReasonCode.WRONG_OPTION,
                ClaimReasonCode.OTHER,
            ),
            ClaimReasonCode.selectableFor(ClaimType.EXCHANGE),
        )
    }

    @Test
    fun `화면 문구는 코드마다 하나씩 정해져 있다`() {
        assertEquals(
            mapOf(
                ClaimReasonCode.CHANGE_OF_MIND to "단순 변심",
                ClaimReasonCode.WRONG_OPTION to "옵션을 잘못 선택했어요",
                ClaimReasonCode.PRICE_MISMATCH to "가격 조건이 맞지 않아요",
                ClaimReasonCode.DELIVERY_DELAY to "배송이 너무 늦어요",
                ClaimReasonCode.DEFECTIVE to "상품이 파손되었거나 불량이에요",
                ClaimReasonCode.WRONG_ITEM to "주문과 다른 상품이 왔어요",
                ClaimReasonCode.NOT_AS_DESCRIBED to "상품 설명과 달라요",
                ClaimReasonCode.OTHER to "기타",
            ),
            ClaimReasonCode.entries.associateWith { it.label },
        )
    }

    @Test
    fun `상세 입력을 요구하는 사유는 기타뿐이다`() {
        assertEquals(listOf(ClaimReasonCode.OTHER), ClaimReasonCode.entries.filter { it.requiresDetail() })
    }

    @Test
    fun `어떤 유형도 받지 않는 사유는 없고 모든 유형이 기타를 받는다`() {
        val selectable = ClaimType.entries.flatMap { ClaimReasonCode.selectableFor(it) }.toSet()

        assertEquals(ClaimReasonCode.entries.toSet(), selectable, "선택할 수 없는 코드가 어휘에 남아 있다")
        ClaimType.entries.forEach { claimType ->
            assertTrue(ClaimReasonCode.OTHER.isSelectableFor(claimType), "$claimType 이 기타를 받지 않는다")
        }
    }

    @Test
    fun `유형 판정은 목록과 같은 답을 낸다`() {
        ClaimType.entries.forEach { claimType ->
            val allowed = ClaimReasonCode.selectableFor(claimType)
            ClaimReasonCode.entries.forEach { reasonCode ->
                if (reasonCode in allowed) {
                    assertTrue(reasonCode.isSelectableFor(claimType), "$claimType + $reasonCode")
                } else {
                    assertFalse(reasonCode.isSelectableFor(claimType), "$claimType + $reasonCode")
                }
            }
        }
    }
}
