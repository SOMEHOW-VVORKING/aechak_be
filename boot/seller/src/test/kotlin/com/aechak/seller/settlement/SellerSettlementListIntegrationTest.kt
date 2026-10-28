package com.aechak.seller.settlement

import com.aechak.domain.seller.seller.enums.SellerStatus
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

/**
 * 셀러 정산 목록 통합 테스트. 본인 정산 한정, 주문 단위 집계, 월·상태 필터, 상태 접기 규칙, 취소 행 제외를 고정한다.
 * 깨지면 셀러가 보는 정산 금액이나 상태가 저장된 원장과 어긋난 것이다.
 */
class SellerSettlementListIntegrationTest : SellerSettlementIntegrationTestBase() {
    @Test
    fun `내 정산만 주문 단위 한 행으로 확정일 최신순으로 반환한다`() {
        val (otherSellerId, _) = openOtherSeller()
        seedSettledOrder(sellerId = otherSellerId)
        val older = seedSettledOrder(confirmedAt = LocalDateTime.of(2026, 6, 10, 10, 0))
        val newer = seedSettledOrder(confirmedAt = LocalDateTime.of(2026, 6, 18, 10, 0), lines = TWO_LINES)

        requestList()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.settlements.length()").value(2))
            .andExpect(jsonPath("$.data.settlements[0].orderId").value(newer.orderPublicId))
            .andExpect(jsonPath("$.data.settlements[1].orderId").value(older.orderPublicId))
            .andExpect(jsonPath("$.data.totalCount").value(2))
            .andExpect(jsonPath("$.data.totalPages").value(1))
            .andExpect(jsonPath("$.data.hasNext").value(false))
    }

    @Test
    fun `품목이 둘인 주문도 한 행으로 접히고 금액은 품목 합이다`() {
        seedSettledOrder(lines = TWO_LINES)

        requestList()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.settlements.length()").value(1))
            .andExpect(jsonPath("$.data.settlements[0].settlementMonth").value("2026-06"))
            .andExpect(jsonPath("$.data.settlements[0].confirmedAt").value("2026-06-18T10:00:00"))
            .andExpect(jsonPath("$.data.settlements[0].expectedPayoutDate").value("2026-06-23"))
            .andExpect(jsonPath("$.data.settlements[0].productAmount").value(TWO_LINES_PRODUCT_AMOUNT))
            .andExpect(jsonPath("$.data.settlements[0].feeAmount").value(TWO_LINES_FEE_AMOUNT))
            .andExpect(jsonPath("$.data.settlements[0].finalAmount").value(TWO_LINES_FINAL_AMOUNT))
            .andExpect(jsonPath("$.data.settlements[0].status").value("pending"))
    }

    @Test
    fun `월 필터는 그 달에 확정된 정산만 반환한다`() {
        val may = seedSettledOrder(confirmedAt = LocalDateTime.of(2026, 5, 20, 9, 0))
        val june = seedSettledOrder(confirmedAt = LocalDateTime.of(2026, 6, 18, 10, 0))

        requestList("month" to "2026-05")
            .andExpect(jsonPath("$.data.settlements.length()").value(1))
            .andExpect(jsonPath("$.data.settlements[0].orderId").value(may.orderPublicId))
            .andExpect(jsonPath("$.data.totalCount").value(1))

        requestList("month" to "2026-06")
            .andExpect(jsonPath("$.data.settlements.length()").value(1))
            .andExpect(jsonPath("$.data.settlements[0].orderId").value(june.orderPublicId))
            .andExpect(jsonPath("$.data.totalCount").value(1))
    }

    @Test
    fun `상태 필터 네 가지가 각각 그 상태의 주문만 반환한다`() {
        val pending = seedSettledOrder()
        val scheduled = seedSettledOrder()
        val completed = seedSettledOrder()
        val held = seedSettledOrder()
        scheduleSettlement(scheduled.settlementIds.single())
        paySettlement(completed.settlementIds.single())
        holdSettlement(held.settlementIds.single())

        assertOnlyOrder("pending", pending.orderPublicId)
        assertOnlyOrder("scheduled", scheduled.orderPublicId)
        assertOnlyOrder("completed", completed.orderPublicId)
        assertOnlyOrder("held", held.orderPublicId)
    }

    @Test
    fun `한 주문 안에서 상태가 갈리면 덜 진행된 쪽으로 접히고 보류가 가장 세다`() {
        val partiallyPaid = seedSettledOrder(lines = TWO_LINES)
        val partiallyHeld = seedSettledOrder(lines = TWO_LINES)
        val scheduledAndPaid = seedSettledOrder(lines = TWO_LINES)
        paySettlement(partiallyPaid.settlementIds.first())
        paySettlement(partiallyHeld.settlementIds.first())
        holdSettlement(partiallyHeld.settlementIds.last())
        paySettlement(scheduledAndPaid.settlementIds.first())
        scheduleSettlement(scheduledAndPaid.settlementIds.last())

        assertOnlyOrder("pending", partiallyPaid.orderPublicId)
        assertOnlyOrder("held", partiallyHeld.orderPublicId)
        assertOnlyOrder("scheduled", scheduledAndPaid.orderPublicId)
    }

    @Test
    fun `부분 상태 필터는 집계 금액을 쪼개지 않는다`() {
        val order = seedSettledOrder(lines = TWO_LINES)
        paySettlement(order.settlementIds.first())

        requestList("status" to "pending")
            .andExpect(jsonPath("$.data.settlements.length()").value(1))
            .andExpect(jsonPath("$.data.settlements[0].productAmount").value(TWO_LINES_PRODUCT_AMOUNT))
            .andExpect(jsonPath("$.data.settlements[0].finalAmount").value(TWO_LINES_FINAL_AMOUNT))
    }

    @Test
    fun `취소된 정산 행은 집계에서 빠지고 전부 취소된 주문은 목록에 없다`() {
        val partiallyCancelled = seedSettledOrder(lines = TWO_LINES)
        val fullyCancelled = seedSettledOrder()
        cancelSettlement(partiallyCancelled.settlementIds.last())
        cancelSettlement(fullyCancelled.settlementIds.single())
        val remaining = settlementsOf(partiallyCancelled.orderId).single { it.id == partiallyCancelled.settlementIds.first() }

        requestList()
            .andExpect(jsonPath("$.data.settlements.length()").value(1))
            .andExpect(jsonPath("$.data.settlements[0].orderId").value(partiallyCancelled.orderPublicId))
            .andExpect(jsonPath("$.data.settlements[0].productAmount").value(remaining.grossAmount.toInt()))
            .andExpect(jsonPath("$.data.settlements[0].finalAmount").value(remaining.netAmount.toInt()))
            .andExpect(jsonPath("$.data.totalCount").value(1))
    }

    @Test
    fun `정산이 없으면 빈 목록과 0건을 반환한다`() {
        requestList()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.settlements.length()").value(0))
            .andExpect(jsonPath("$.data.totalCount").value(0))
            .andExpect(jsonPath("$.data.totalPages").value(0))
            .andExpect(jsonPath("$.data.hasNext").value(false))

        requestList("month" to "2026-06", "status" to "completed")
            .andExpect(jsonPath("$.data.settlements.length()").value(0))
            .andExpect(jsonPath("$.data.totalCount").value(0))
    }

    @Test
    fun `오프셋 페이지네이션과 페이지 정보가 동작한다`() {
        seedSettledOrder(confirmedAt = LocalDateTime.of(2026, 6, 1, 10, 0))
        seedSettledOrder(confirmedAt = LocalDateTime.of(2026, 6, 2, 10, 0))
        val latest = seedSettledOrder(confirmedAt = LocalDateTime.of(2026, 6, 3, 10, 0))

        requestList("size" to "2")
            .andExpect(jsonPath("$.data.settlements.length()").value(2))
            .andExpect(jsonPath("$.data.settlements[0].orderId").value(latest.orderPublicId))
            .andExpect(jsonPath("$.data.totalCount").value(3))
            .andExpect(jsonPath("$.data.totalPages").value(2))
            .andExpect(jsonPath("$.data.hasNext").value(true))

        requestList("size" to "2", "page" to "1")
            .andExpect(jsonPath("$.data.settlements.length()").value(1))
            .andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.hasNext").value(false))
    }

    @Test
    fun `주문 행이 없는 정산 행은 목록과 총건수에서 함께 빠지고 정상 주문은 남는다`() {
        seedSettlementWithoutOrder()
        val normal = seedSettledOrder()

        requestList()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.settlements.length()").value(1))
            .andExpect(jsonPath("$.data.settlements[0].orderId").value(normal.orderPublicId))
            .andExpect(jsonPath("$.data.totalCount").value(1))

        requestList("month" to "2026-06", "status" to "pending")
            .andExpect(jsonPath("$.data.settlements.length()").value(1))
            .andExpect(jsonPath("$.data.settlements[0].orderId").value(normal.orderPublicId))
            .andExpect(jsonPath("$.data.totalCount").value(1))
    }

    @Test
    fun `page가 Int 최댓값이어도 다음 페이지가 있다고 하지 않는다`() {
        seedSettledOrder()

        requestList("page" to Int.MAX_VALUE.toString())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.settlements.length()").value(0))
            .andExpect(jsonPath("$.data.totalCount").value(1))
            .andExpect(jsonPath("$.data.hasNext").value(false))
    }

    @Test
    fun `월 형식과 상태 어휘가 틀리면 400으로 거절한다`() {
        requestList("month" to "2026-6")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
        requestList("month" to "2026-13")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
        requestList("status" to "cancelled")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
        requestList("size" to "0")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
    }

    @Test
    fun `휴점 셀러는 자기 정산을 보고 정지 셀러는 403 70003으로 막힌다`() {
        seedSettledOrder()
        changeSellerStatus(sellerUserId, SellerStatus.PAUSED)

        requestList()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.settlements.length()").value(1))

        changeSellerStatus(sellerUserId, SellerStatus.SUSPENDED)
        requestList()
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(70003))
    }

    /** 제재와 코드를 나눈다 — 정산 접근 거절(70003)은 입점한 적 없는 계정의 원인을 설명하지 못한다. */
    @Test
    fun `비셀러는 제재 셀러와 다른 10004로 막힌다`() {
        requestList(asToken = mintAccessToken(createActiveUser()))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(10004))
    }

    private fun assertOnlyOrder(
        status: String,
        orderPublicId: String,
    ) {
        requestList("status" to status)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.settlements.length()").value(1))
            .andExpect(jsonPath("$.data.settlements[0].orderId").value(orderPublicId))
            .andExpect(jsonPath("$.data.settlements[0].status").value(status))
            .andExpect(jsonPath("$.data.totalCount").value(1))
    }
}
