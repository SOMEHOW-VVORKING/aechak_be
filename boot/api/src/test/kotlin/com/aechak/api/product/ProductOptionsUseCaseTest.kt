package com.aechak.api.product

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.product.usecase.ProductUseCase
import com.aechak.common.error.BusinessException
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.error.ProductErrorCode
import com.aechak.domain.product.option.OptionCombination
import com.aechak.domain.product.option.OptionGroup
import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.seller.seller.Seller
import com.aechak.domain.seller.seller.enums.SellerStatus
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 통합 테스트 — 옵션 조회의 노출 게이트(상세와 동일 술어)·활성 필터·선택 불가 조합 제외·정렬·재고 노출 경계를
 * 실 배선(실 MySQL 컨테이너)으로 검증한다. 옵션 값·조합-값 연결은 아직 도메인 생성 경로가 없어(등록 티켓 몫)
 * 명시 id 네이티브 INSERT로 픽스처를 만든다 — 명시 id라 optionValueIds 단언이 정확해진다.
 */
class ProductOptionsUseCaseTest : IntegrationTestBase() {
    @Autowired
    lateinit var productUseCase: ProductUseCase

    private val defaultSellerId = 77L

    // ---------- 픽스처 헬퍼 (tx.execute 안에서만 호출) ----------

    private fun persistVisibleProduct(name: String = "옵션상품"): Product {
        if (em.find(Seller::class.java, defaultSellerId) == null) {
            em.persist(Seller.open(userId = defaultSellerId, storeName = "store-$defaultSellerId", baseShippingFee = 3000L))
        }
        val root = Category.create(null, 1, "강아지", null, 1)
        em.persist(root)
        val mid = Category.create(root, 2, "사료간식", null, 1)
        em.persist(mid)
        val product =
            Product.register(
                category = mid,
                sellerId = defaultSellerId,
                name = name,
                description = null,
                representativeImageKey = null,
                regularPrice = 10000L,
                discountPrice = null,
                discountStartAt = null,
                discountEndAt = null,
            )
        em.persist(product)
        return product
    }

    private fun persistGroup(
        product: Product,
        name: String,
        sortOrder: Int,
    ): OptionGroup {
        val group = OptionGroup.create(product, name, sortOrder)
        em.persist(group)
        em.flush()
        return group
    }

    /** 옵션 값은 루트 경유 생성 경로가 아직 없어 명시 id 네이티브 INSERT로 만든다. */
    private fun insertValue(
        valueId: Long,
        groupId: Long,
        name: String,
        sortOrder: Int,
        active: Boolean = true,
    ) {
        em
            .createNativeQuery(
                "insert into option_values (id, option_group_id, name, sort_order, is_active, created_at, updated_at) " +
                    "values (:id, :groupId, :name, :sortOrder, :active, now(), now())",
            ).setParameter("id", valueId)
            .setParameter("groupId", groupId)
            .setParameter("name", name)
            .setParameter("sortOrder", sortOrder)
            .setParameter("active", active)
            .executeUpdate()
    }

    private fun persistCombination(
        product: Product,
        name: String,
        additionalPrice: Long = 0L,
        stock: Int,
        valueIds: List<Long> = emptyList(),
        deactivated: Boolean = false,
    ): OptionCombination {
        val combination =
            OptionCombination.create(
                product = product,
                name = name,
                additionalPrice = additionalPrice,
                stockQuantity = stock,
                valueSignature = valueIds.sorted().joinToString("-").ifEmpty { name },
            )
        if (deactivated) combination.deactivate()
        em.persist(combination)
        em.flush()
        valueIds.forEach { valueId ->
            em
                .createNativeQuery(
                    "insert into option_combination_values (option_combination_id, option_value_id, created_at, updated_at) " +
                        "values (:combinationId, :valueId, now(), now())",
                ).setParameter("combinationId", combination.id)
                .setParameter("valueId", valueId)
                .executeUpdate()
        }
        return combination
    }

    private fun overrideGroupInactive(groupId: Long) {
        em
            .createQuery("update OptionGroup g set g.isActive = false where g.id = :id")
            .setParameter("id", groupId)
            .executeUpdate()
    }

    private fun getOptions(publicId: String) = productUseCase.getProductOptions(publicId)

    private fun assertProductNotFound(block: () -> Unit) {
        val exception = assertFailsWith<BusinessException>(block = block)
        assertEquals(ProductErrorCode.PRODUCT_NOT_FOUND, exception.errorCode)
    }

    // ---------- 그룹·값 ----------

    @Test
    fun `그룹과 값은 sortOrder 순으로 정렬되어 반환된다`() {
        val publicId =
            tx.execute {
                val product = persistVisibleProduct()
                val size = persistGroup(product, "용량", 2)
                val flavor = persistGroup(product, "맛", 1)
                insertValue(21L, size.id, "1kg", 1)
                insertValue(12L, flavor.id, "치킨", 2)
                insertValue(11L, flavor.id, "연어", 1)
                product.publicId
            }!!

        val groups = getOptions(publicId).optionGroups

        assertEquals(listOf("맛", "용량"), groups.map { it.name })
        assertEquals(listOf("연어", "치킨"), groups.first().values.map { it.name })
    }

    @Test
    fun `비활성 그룹과 비활성 값은 제외된다`() {
        val publicId =
            tx.execute {
                val product = persistVisibleProduct()
                val flavor = persistGroup(product, "맛", 1)
                val hidden = persistGroup(product, "숨김그룹", 2)
                insertValue(11L, flavor.id, "연어", 1)
                insertValue(12L, flavor.id, "치킨", 2, active = false)
                insertValue(21L, hidden.id, "안보임", 1)
                overrideGroupInactive(hidden.id)
                product.publicId
            }!!

        val groups = getOptions(publicId).optionGroups

        assertEquals(listOf("맛"), groups.map { it.name })
        assertEquals(listOf("연어"), groups.single().values.map { it.name })
    }

    // ---------- 조합 ----------

    @Test
    fun `조합의 optionValueIds는 연결된 값 id 오름차순이고 조합은 한 번만 나타난다`() {
        val ids =
            tx.execute {
                val product = persistVisibleProduct()
                val flavor = persistGroup(product, "맛", 1)
                val size = persistGroup(product, "용량", 2)
                insertValue(11L, flavor.id, "연어", 1)
                insertValue(21L, size.id, "1kg", 1)
                val combination =
                    persistCombination(product, "연어 / 1kg", additionalPrice = 500L, stock = 10, valueIds = listOf(21L, 11L))
                product.publicId to combination.id
            }!!

        val combinations = getOptions(ids.first).optionCombinations

        assertEquals(1, combinations.size) // 값 2개를 연결해도 조합은 한 행으로 조립된다
        val single = combinations.single()
        assertEquals(ids.second, single.optionCombinationId)
        assertEquals("연어 / 1kg", single.name)
        assertEquals(500L, single.additionalPrice)
        assertEquals(listOf(11L, 21L), single.optionValueIds)
    }

    @Test
    fun `재고는 20개 이하일 때만 노출되고 0이면 품절로 판정된다`() {
        val publicId =
            tx.execute {
                val product = persistVisibleProduct()
                persistCombination(product, "품절", stock = 0)
                persistCombination(product, "경계노출", stock = 20)
                persistCombination(product, "숨김", stock = 21)
                product.publicId
            }!!

        val byName = getOptions(publicId).optionCombinations.associateBy { it.name }

        assertEquals(0, byName.getValue("품절").remainingStock)
        assertTrue(byName.getValue("품절").soldOut)
        assertEquals(20, byName.getValue("경계노출").remainingStock)
        assertFalse(byName.getValue("경계노출").soldOut)
        assertNull(byName.getValue("숨김").remainingStock)
        assertFalse(byName.getValue("숨김").soldOut)
    }

    @Test
    fun `비활성 값이나 비활성 그룹을 참조하는 조합은 제외된다`() {
        val publicId =
            tx.execute {
                val product = persistVisibleProduct()
                val flavor = persistGroup(product, "맛", 1)
                val packaging = persistGroup(product, "포장", 2)
                insertValue(11L, flavor.id, "연어", 1)
                insertValue(12L, flavor.id, "치킨", 2, active = false)
                insertValue(21L, packaging.id, "기본포장", 1)
                persistCombination(product, "연어조합", stock = 5, valueIds = listOf(11L))
                persistCombination(product, "치킨조합", stock = 5, valueIds = listOf(12L))
                persistCombination(product, "포장조합", stock = 5, valueIds = listOf(11L, 21L))
                overrideGroupInactive(packaging.id)
                product.publicId
            }!!

        val options = getOptions(publicId)

        // 비활성 값(치킨), 비활성 그룹 소속 값(기본포장)을 참조하는 조합은 선택 불가라 노출하지 않는다
        assertEquals(listOf("연어조합"), options.optionCombinations.map { it.name })
        val exposedValueIds = options.optionGroups.flatMap { group -> group.values.map { it.optionValueId } }.toSet()
        assertTrue(options.optionCombinations.all { exposedValueIds.containsAll(it.optionValueIds) })
    }

    @Test
    fun `비활성 조합은 제외된다`() {
        val publicId =
            tx.execute {
                val product = persistVisibleProduct()
                persistCombination(product, "판매중조합", stock = 5)
                persistCombination(product, "중단조합", stock = 5, deactivated = true)
                product.publicId
            }!!

        assertEquals(listOf("판매중조합"), getOptions(publicId).optionCombinations.map { it.name })
    }

    @Test
    fun `옵션이 없는 상품은 빈 목록으로 성공한다`() {
        val publicId = tx.execute { persistVisibleProduct("단일상품").publicId }!!

        val options = getOptions(publicId)

        assertTrue(options.optionGroups.isEmpty())
        assertTrue(options.optionCombinations.isEmpty())
    }

    // ---------- 노출 게이트 ----------

    @Test
    fun `노출 정책에 걸린 상품의 옵션 조회는 PRODUCT_NOT_FOUND 예외가 발생한다`() {
        assertProductNotFound { getOptions("01JUNKNOWNXXXXXXXXXXXXXX00") }

        val suspendedPublicId =
            tx.execute {
                val product = persistVisibleProduct("중지상품")
                persistCombination(product, "기본", stock = 5)
                em
                    .createQuery("update Product p set p.saleStatus = :status where p.id = :id")
                    .setParameter("status", SaleStatus.SUSPENDED)
                    .setParameter("id", product.id)
                    .executeUpdate()
                product.publicId
            }!!
        assertProductNotFound { getOptions(suspendedPublicId) }

        val inactiveSellerPublicId =
            tx.execute {
                val sellerId = 300L
                em.persist(Seller.open(userId = sellerId, storeName = "닫힌상점", baseShippingFee = 3000L))
                val root = Category.create(null, 1, "고양이", null, 2)
                em.persist(root)
                val mid = Category.create(root, 2, "장난감", null, 1)
                em.persist(mid)
                val product =
                    Product.register(
                        category = mid,
                        sellerId = sellerId,
                        name = "셀러중지상품",
                        description = null,
                        representativeImageKey = null,
                        regularPrice = 10000L,
                        discountPrice = null,
                        discountStartAt = null,
                        discountEndAt = null,
                    )
                em.persist(product)
                em.flush()
                em
                    .createQuery("update Seller s set s.status = :status where s.userId = :id")
                    .setParameter("status", SellerStatus.SUSPENDED)
                    .setParameter("id", sellerId)
                    .executeUpdate()
                product.publicId
            }!!
        assertProductNotFound { getOptions(inactiveSellerPublicId) }
    }
}
