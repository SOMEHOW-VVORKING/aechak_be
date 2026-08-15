package com.aechak.seller.product

import com.aechak.api.support.IntegrationTestBase
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.option.OptionCombination
import com.aechak.domain.product.option.event.OptionCombinationChangedEvent
import com.aechak.domain.product.option.repository.OptionCombinationRepository
import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.product.product.repository.ProductRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

/**
 * 영속 계약 통합 테스트. 잠금 조회와 재고 반영, 재고 존재 판정을 실 MySQL에서 고정함.
 * 깨지면 잔량을 넘는 감소가 음수로 저장되거나, 남의 조합이 잠금 조회로 넘어오거나,
 * 비활성 조합의 재고까지 세어 품절 판정이 어긋남.
 */
class OptionStockPersistenceIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var optionCombinationRepository: OptionCombinationRepository

    @Autowired
    private lateinit var productRepository: ProductRepository

    private var leafCategoryId = 0L

    @BeforeEach
    fun seedCategory() {
        tx.execute {
            val root = Category.create(null, Category.ROOT_DEPTH, "강아지", null, 1)
            em.persist(root)
            val mid = Category.create(root, Category.MID_DEPTH, "사료", null, 1)
            em.persist(mid)
            val leaf = Category.create(mid, Category.LEAF_DEPTH, "건사료", null, 1)
            em.persist(leaf)
            leafCategoryId = leaf.id
        }
    }

    @Test
    fun `감소는 잔량 경계까지 반영되고 넘어서면 0에서 멈춘다`() {
        val (productId, combinationId) = persistCombination(stockQuantity = 15)

        val exact = changeStock(productId, combinationId, -15)
        assertEquals(-15, exact, "잔량과 정확히 같은 감소는 전부 반영돼야 한다")
        assertEquals(0, stockOf(combinationId), "재고가 0까지 내려가야 한다")

        val over = changeStock(productId, combinationId, -1)
        assertEquals(0, over, "더 뺄 게 없으면 반영량이 0이어야 한다")
        assertEquals(0, stockOf(combinationId), "재고가 음수로 저장되면 안 된다")
    }

    @Test
    fun `Int 최솟값 감소도 0에서 멈춘다`() {
        val (productId, combinationId) = persistCombination(stockQuantity = 10)

        val applied = changeStock(productId, combinationId, Int.MIN_VALUE)

        assertEquals(-10, applied, "가진 만큼만 반영돼야 한다")
        assertEquals(0, stockOf(combinationId), "부호 반전이 감싸여도 음수가 되면 안 된다")
    }

    @Test
    fun `조회는 조합과 상품의 소속이 맞을 때만 행을 준다`() {
        val (productId, combinationId) = persistCombination(stockQuantity = 10)
        val (otherProductId, _) = persistCombination(stockQuantity = 10)

        tx.execute {
            assertNotNull(
                optionCombinationRepository.findByIdAndProductIdForUpdate(combinationId, productId),
                "자기 상품의 조합은 나와야 한다",
            )
            assertNull(
                optionCombinationRepository.findByIdAndProductIdForUpdate(combinationId, otherProductId),
                "다른 상품 id와의 쌍은 없어야 한다",
            )
            assertNull(
                optionCombinationRepository.findByIdAndProductIdForUpdate(999_999L, productId),
                "없는 조합은 없어야 한다",
            )
        }
    }

    @Test
    fun `saveNow는 커밋 전에 변경 후 값과 시각을 확정한다`() {
        val (productId, combinationId) = persistCombination(stockQuantity = 30)
        val before = tx.execute { optionCombinationRepository.findById(combinationId)!!.updatedAt }!!

        tx.execute {
            val combination = optionCombinationRepository.findByIdAndProductIdForUpdate(combinationId, productId)!!
            combination.changeStock(-3)
            optionCombinationRepository.saveNow(combination)

            assertEquals(27, combination.stockQuantity, "반영 직후 변경 후 재고여야 한다")
            assertNotEquals(before, combination.updatedAt, "flush가 updatedAt도 함께 갱신해야 한다")
        }
    }

    @Test
    fun `활성 재고 판정은 활성이고 재고가 남은 조합만 센다`() {
        val (productId, combinationId) = persistCombination(stockQuantity = 10)

        assertTrue(
            tx.execute { optionCombinationRepository.existsActiveStock(productId) }!!,
            "활성 조합에 재고가 있으면 참이어야 한다",
        )

        changeStock(productId, combinationId, -10)
        assertFalse(
            tx.execute { optionCombinationRepository.existsActiveStock(productId) }!!,
            "재고가 전부 0이면 거짓이어야 한다",
        )

        tx.execute {
            val combination = optionCombinationRepository.findByIdAndProductIdForUpdate(combinationId, productId)!!
            combination.changeStock(5)
            combination.deactivate()
            optionCombinationRepository.saveNow(combination)
        }
        assertFalse(
            tx.execute { optionCombinationRepository.existsActiveStock(productId) }!!,
            "재고가 남아도 비활성 조합뿐이면 거짓이어야 한다",
        )
    }

    /**
     * 잠금 회귀 방어. 스레드 둘이 같은 조합을 동시에 깎는다.
     * 잠그면 뒤 트랜잭션이 앞의 커밋을 기다렸다가 갱신된 값 위에서 계산해 10에서 4가 됨.
     * 잠금이 없으면 둘 다 10을 읽어 7을 쓰므로 앞의 감소가 사라짐.
     */
    @Test
    fun `같은 조합을 동시에 깎아도 감소가 서로를 덮지 않는다`() {
        val (productId, combinationId) = persistCombination(stockQuantity = 10)
        val firstLocked = CountDownLatch(1)

        val first =
            thread {
                tx.execute {
                    val combination = optionCombinationRepository.findByIdAndProductIdForUpdate(combinationId, productId)!!
                    firstLocked.countDown()
                    // 잠금을 쥔 채 머물러야 뒤 트랜잭션이 잠금 대기에 걸림. 먼저 커밋돼도 결과는 같음
                    Thread.sleep(300)
                    combination.changeStock(-3)
                    optionCombinationRepository.saveNow(combination)
                }
            }

        firstLocked.await()
        changeStock(productId, combinationId, -3)
        first.join()

        assertEquals(4, stockOf(combinationId), "10에서 3씩 두 번 빠져 4여야 한다. 7이면 뒤 트랜잭션이 낡은 값을 덮어쓴 것")
    }

    /**
     * 상품 잠금 회귀 방어. 조합 둘을 각각 고친 판정 둘이 겹치는 상황을 재현함.
     * 앞 판정이 재고 없음을 읽고 아직 안 쓴 사이에 뒤 요청이 재고를 채우는데, 뒤 판정은 상태를
     * 그대로 두므로 아무것도 안 씀. 잠그지 않으면 앞의 낡은 품절이 최종 상태로 굳음.
     */
    @Test
    fun `앞 판정이 쓰기 전에 재고가 채워지면 뒤 판정이 이를 바로잡는다`() {
        val (productId, firstId, secondId) = persistTwoCombinations()
        changeStock(productId, firstId, -5)
        val stockRead = CountDownLatch(1)

        val stale =
            thread {
                tx.execute {
                    val product = productRepository.findByIdForUpdate(productId)!!
                    val hasStock = optionCombinationRepository.existsActiveStock(productId)
                    stockRead.countDown()
                    // 잠금을 쥔 채 머물러야 뒤 판정이 잠금 대기에 걸림
                    Thread.sleep(300)
                    product.syncSaleStatusWithStock(hasStock)
                    productRepository.saveNow(product)
                }
            }

        stockRead.await()
        changeStock(productId, secondId, 5)
        tx.execute {
            val product = productRepository.findByIdForUpdate(productId)!!
            product.syncSaleStatusWithStock(optionCombinationRepository.existsActiveStock(productId))
            productRepository.saveNow(product)
        }
        stale.join()

        assertEquals(
            SaleStatus.ON_SALE,
            tx.execute { productRepository.findByIdForUpdate(productId)!!.saleStatus },
            "재고가 5 남았으므로 판매중이어야 한다. 품절이면 낡은 판정이 뒤늦게 덮어쓴 것",
        )
    }

    @Test
    fun `조합 변경 이벤트는 상품 id와 조합 id를 각각 싣는다`() {
        // 상품과 조합이 나란히 채번돼 id가 같으면 자리를 바꿔 실어도 안 드러남. 조합만 하나 더 만들어 어긋냄
        val (spareProductId, _) = persistCombination(stockQuantity = 1)
        tx.execute {
            em.persist(
                OptionCombination.create(
                    product = em.find(Product::class.java, spareProductId),
                    name = "여분",
                    additionalPrice = 0L,
                    stockQuantity = 1,
                    valueSignature = "sig-spare",
                ),
            )
        }
        val (productId, combinationId) = persistCombination(stockQuantity = 10)
        assertNotEquals(productId, combinationId, "두 id가 어긋나야 자리를 바꿔 실은 것을 잡을 수 있다")

        val event =
            tx.execute {
                val combination = optionCombinationRepository.findByIdAndProductIdForUpdate(combinationId, productId)!!
                combination.registerChangedEvent()
                combination.events.single() as OptionCombinationChangedEvent
            }!!

        assertEquals(productId, event.productId, "이벤트가 소속 상품 id를 실어야 한다")
        assertEquals(combinationId, event.combinationId, "이벤트가 조합 id를 실어야 한다")
    }

    private fun changeStock(
        productId: Long,
        combinationId: Long,
        delta: Int,
    ): Int =
        tx.execute {
            val combination = optionCombinationRepository.findByIdAndProductIdForUpdate(combinationId, productId)!!
            combination.changeStock(delta).also { optionCombinationRepository.saveNow(combination) }
        }!!

    private fun persistTwoCombinations(): Triple<Long, Long, Long> {
        val (productId, firstId) = persistCombination(stockQuantity = 5)
        val secondId =
            tx.execute {
                val second =
                    OptionCombination.create(
                        product = em.find(Product::class.java, productId),
                        name = "5kg",
                        additionalPrice = 0L,
                        stockQuantity = 0,
                        valueSignature = "sig-second-$productId",
                    )
                em.persist(second)
                second.id
            }!!
        return Triple(productId, firstId, secondId)
    }

    private fun persistCombination(stockQuantity: Int): Pair<Long, Long> =
        tx.execute {
            val product =
                Product.register(
                    category = em.find(Category::class.java, leafCategoryId),
                    sellerId = 1L,
                    name = "연어 건사료 2kg",
                    description = null,
                    representativeImageKey = "products/thumb.png",
                    regularPrice = 25_000L,
                    discountPrice = null,
                    discountStartAt = null,
                    discountEndAt = null,
                )
            em.persist(product)
            val combination =
                OptionCombination.create(
                    product = product,
                    name = "2kg",
                    additionalPrice = 0L,
                    stockQuantity = stockQuantity,
                    valueSignature = "sig-${product.publicId}",
                )
            em.persist(combination)
            product.id to combination.id
        }!!

    private fun stockOf(combinationId: Long): Int = tx.execute { optionCombinationRepository.findById(combinationId)!!.stockQuantity }!!
}
