package com.aechak.api.product

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.product.port.ProductCatalogCondition
import com.aechak.application.product.port.ProductCatalogQueryPort
import com.aechak.application.product.port.ProductCatalogSort
import com.aechak.application.product.port.result.ProductCatalogView
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.product.Product
import com.aechak.domain.seller.seller.Seller
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/** SQL 유효가격(effectivePrice CASE)과 Kotlin ProductPricing 가격 선택의 계약 테스트 */
class ProductCatalogEffectivePriceContractTest : IntegrationTestBase() {
    @Autowired
    lateinit var catalogQueryPort: ProductCatalogQueryPort

    private val anchorNow = LocalDateTime.of(2026, 7, 20, 12, 0)

    @Test
    fun `PRICE_ASC의 SQL 정렬 가격은 anchorNow 기준 Kotlin 판매가와 일치한다`() {
        val expectedPublicIds =
            tx.execute {
                em.persist(Seller.open(userId = 1L, storeName = "스토어", baseShippingFee = 3000L))
                val category = Category.create(null, 1, "루트", null, 1)
                em.persist(category)

                fun product(
                    name: String,
                    discount: Long?,
                    start: LocalDateTime?,
                    end: LocalDateTime?,
                ): String {
                    val p = Product.register(category, 1L, name, null, null, 10000L, discount, start, end)
                    em.persist(p)
                    return p.publicId
                }

                setOf(
                    product("시작직전", 5000L, anchorNow.plusMinutes(1), anchorNow.plusDays(1)), // 아직 미적용 → 정가
                    product("시작정각", 5000L, anchorNow, anchorNow.plusDays(1)), // 경계 포함 → 할인가
                    product("종료정각", 5000L, anchorNow.minusDays(1), anchorNow), // 경계 포함 → 할인가
                    product("종료직후", 5000L, anchorNow.minusDays(1), anchorNow.minusMinutes(1)), // 만료 → 정가
                    product("무기한할인", 5000L, anchorNow.minusDays(1), null), // 종료 없음 → 할인가
                    product("할인없음", null, null, null), // 정가
                )
            }!!

        val views =
            catalogQueryPort.findVisiblePage(
                ProductCatalogCondition(
                    categoryId = null,
                    sort = ProductCatalogSort.PRICE_ASC,
                    lastId = null,
                    lastPrice = null,
                    limit = expectedPublicIds.size + 1,
                    now = anchorNow,
                ),
            )

        assertEquals(expectedPublicIds, views.map { it.publicId }.toSet())
        assertEquals(expectedPublicIds.size, views.size)

        views.forEach { view: ProductCatalogView ->
            assertEquals(
                view.pricing().sellingPriceAt(anchorNow),
                view.sortPriceAtAnchor,
                "publicId=${view.publicId}",
            )
        }
    }
}
