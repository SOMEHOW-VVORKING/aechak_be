package com.aechak.seller.settlement

import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

/**
 * 정산월 목록 통합 테스트. 화면의 월 Select가 이 목록을 그대로 쓰므로 "고를 수 있는 월에는 정산이 있다"가 계약이다.
 * 깨지면 셀러가 고른 월이 빈 목록을 돌려주거나, 정산이 있는 달을 고를 수 없게 된다.
 */
class SellerSettlementMonthsIntegrationTest : SellerSettlementIntegrationTestBase() {
    @Test
    fun `정산이 있는 월만 최신순으로 반환한다`() {
        seedSettledOrder(confirmedAt = LocalDateTime.of(2026, 4, 2, 9, 0))
        seedSettledOrder(confirmedAt = LocalDateTime.of(2026, 6, 18, 10, 0))
        seedSettledOrder(confirmedAt = LocalDateTime.of(2026, 6, 20, 10, 0))

        requestMonths()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.months.length()").value(2))
            .andExpect(jsonPath("$.data.months[0]").value("2026-06"))
            .andExpect(jsonPath("$.data.months[1]").value("2026-04"))
    }

    @Test
    fun `다른 셀러의 정산월은 섞이지 않는다`() {
        val (otherSellerId, otherToken) = openOtherSeller()
        seedSettledOrder(confirmedAt = LocalDateTime.of(2026, 6, 18, 10, 0))
        seedSettledOrder(sellerId = otherSellerId, confirmedAt = LocalDateTime.of(2026, 3, 5, 10, 0))

        requestMonths()
            .andExpect(jsonPath("$.data.months.length()").value(1))
            .andExpect(jsonPath("$.data.months[0]").value("2026-06"))

        requestMonths(asToken = otherToken)
            .andExpect(jsonPath("$.data.months.length()").value(1))
            .andExpect(jsonPath("$.data.months[0]").value("2026-03"))
    }

    @Test
    fun `정산 행이 전부 취소된 월은 고를 수 없다`() {
        val cancelled = seedSettledOrder(confirmedAt = LocalDateTime.of(2026, 5, 7, 10, 0))
        seedSettledOrder(confirmedAt = LocalDateTime.of(2026, 6, 18, 10, 0))
        cancelSettlement(cancelled.settlementIds.single())

        requestMonths()
            .andExpect(jsonPath("$.data.months.length()").value(1))
            .andExpect(jsonPath("$.data.months[0]").value("2026-06"))
    }

    @Test
    fun `최근 24개월까지만 반환하고 상한은 행 수가 아니라 월 수에 걸린다`() {
        val confirmedAt = LocalDateTime.of(2026, 6, 18, 10, 0)
        repeat(26) { seedSettledOrder(confirmedAt = confirmedAt.minusMonths(it.toLong())) }
        // 같은 달에 주문이 둘이어도 월 하나로 접혀야 한다 — 행을 세면 가장 오래된 월이 밀려 나간다
        seedSettledOrder(confirmedAt = confirmedAt.minusDays(2))

        requestMonths()
            .andExpect(jsonPath("$.data.months.length()").value(24))
            .andExpect(jsonPath("$.data.months[0]").value("2026-06"))
            .andExpect(jsonPath("$.data.months[23]").value("2024-07"))
    }

    @Test
    fun `주문 행이 없는 정산 행의 월은 고를 수 없다`() {
        seedSettlementWithoutOrder(confirmedAt = LocalDateTime.of(2026, 2, 3, 10, 0))
        seedSettledOrder(confirmedAt = LocalDateTime.of(2026, 6, 18, 10, 0))

        requestMonths()
            .andExpect(jsonPath("$.data.months.length()").value(1))
            .andExpect(jsonPath("$.data.months[0]").value("2026-06"))
    }

    @Test
    fun `정산이 없으면 빈 목록을 반환한다`() {
        requestMonths()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.months.length()").value(0))
    }
}
