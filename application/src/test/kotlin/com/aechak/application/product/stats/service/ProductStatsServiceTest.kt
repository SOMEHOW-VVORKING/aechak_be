package com.aechak.application.product.stats.service

import com.aechak.domain.product.stats.ProductStats
import com.aechak.domain.product.stats.repository.ProductStatsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 단위 테스트 — 배치 조회 결과를 productId 키로 매핑하는 규칙을 고정한다. */
class ProductStatsServiceTest {
    /** 저장된 통계 중 요청 id에 해당하는 것만 돌려주는 페이크 */
    private class FakeProductStatsRepository(
        private val stored: List<ProductStats>,
    ) : ProductStatsRepository {
        override fun findAllByProductIds(productIds: Collection<Long>): List<ProductStats> = stored.filter { it.productId in productIds }

        override fun increaseLikeCount(productId: Long): Int = error("이 테스트에서 호출하지 않는다")

        override fun decreaseLikeCount(productId: Long): Int = error("이 테스트에서 호출하지 않는다")
    }

    @Test
    fun `조회한 통계를 productId를 키로 매핑한다`() {
        val service =
            ProductStatsService(
                FakeProductStatsRepository(listOf(ProductStats.init(1L), ProductStats.init(2L))),
            )

        val result = service.getStatsByProductIds(listOf(1L, 2L))

        assertEquals(setOf(1L, 2L), result.keys)
        assertEquals(1L, result[1L]?.productId)
        assertEquals(2L, result[2L]?.productId)
    }

    @Test
    fun `조회되지 않은 id는 결과에 포함하지 않는다`() {
        val service =
            ProductStatsService(FakeProductStatsRepository(listOf(ProductStats.init(1L))))

        val result = service.getStatsByProductIds(listOf(1L, 99L))

        assertEquals(setOf(1L), result.keys)
        assertNull(result[99L])
    }
}
