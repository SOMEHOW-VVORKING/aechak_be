package com.aechak.api.product.search

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.product.like.service.ProductLikeStatusService
import com.aechak.application.product.product.port.view.ProductCatalogView
import com.aechak.application.product.search.facade.ProductKeywordSearchFacade
import com.aechak.application.product.search.port.ProductKeywordSearchCondition
import com.aechak.application.product.search.port.ProductKeywordSearchFirstPage
import com.aechak.application.product.search.port.ProductKeywordSearchPort
import com.aechak.application.product.search.service.ProductKeywordSearchService
import com.aechak.application.product.search.service.ProductSearchReindexService
import com.aechak.application.product.search.usecase.query.ProductKeywordSearchQuery
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.category.repository.CategoryRepository
import com.aechak.domain.product.like.ProductLike
import com.aechak.domain.product.product.Product
import com.aechak.domain.seller.seller.Seller
import com.aechak.infra.elasticsearch.product.search.ElasticsearchProductKeywordSearchAdapter
import com.aechak.infra.persistence.product.search.ProductKeywordSearchQueryAdapter
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Duration

/**
 * 키워드 검색 Facade가 DB를 읽지 않는 검색 포트를 트랜잭션과 커넥션 없이 호출하고,
 * DB 검색 포트는 읽기 트랜잭션 하나 안에서 호출하는지 고정하는 통합 테스트
 */
class ProductKeywordSearchTransactionIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var categoryRepository: CategoryRepository

    @Autowired
    private lateinit var productLikeStatusService: ProductLikeStatusService

    @Autowired
    private lateinit var eventPublisher: ApplicationEventPublisher

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var jpaAdapter: ProductKeywordSearchQueryAdapter

    @Autowired
    private lateinit var esAdapter: ElasticsearchProductKeywordSearchAdapter

    @Autowired
    private lateinit var reindexService: ProductSearchReindexService

    @Test
    fun `Elasticsearch 검색은 첫 페이지와 커서 페이지 모두 트랜잭션과 바인딩된 커넥션 없이 호출한다`() {
        persistFixture()
        reindexService.reindexAll()
        val probe = TxProbe(esAdapter)
        val facade = facadeOf(probe)
        val userId = createActiveUser()

        val first = facade.searchProducts(ProductKeywordSearchQuery(keyword = "사료", size = 2), userId)
        facade.searchProducts(ProductKeywordSearchQuery(keyword = "사료", size = 2, cursor = checkNotNull(first.nextCursor)), userId)
        facade.searchProducts(ProductKeywordSearchQuery(keyword = "사료", size = 2), null)

        assertEquals(listOf("searchFirstPage", "findIdByPublicId", "search", "searchFirstPage"), probe.observed.map { it.call })
        probe.observed.forEach {
            assertEquals(Observation(it.call, active = false, readOnly = false, unbound = true), it)
        }
    }

    @Test
    fun `JPA 검색은 첫 페이지와 커서 페이지 모두 읽기 트랜잭션 안에서 호출한다`() {
        persistFixture()
        val probe = TxProbe(jpaAdapter)
        val facade = facadeOf(probe)
        val userId = createActiveUser()

        val first = facade.searchProducts(ProductKeywordSearchQuery(keyword = "사료", size = 2), userId)
        facade.searchProducts(ProductKeywordSearchQuery(keyword = "사료", size = 2, cursor = checkNotNull(first.nextCursor)), userId)
        facade.searchProducts(ProductKeywordSearchQuery(keyword = "사료", size = 2), null)

        assertEquals(listOf("searchFirstPage", "findIdByPublicId", "search", "searchFirstPage"), probe.observed.map { it.call })
        probe.observed.forEach {
            assertEquals(Observation(it.call, active = true, readOnly = true, unbound = false), it)
        }
    }

    @Test
    fun `Elasticsearch 검색도 로그인 첫 페이지만 최근 검색어로 적재하고 찜 여부를 채운다`() {
        val liked = persistFixture().first()
        reindexService.reindexAll()
        val facade = facadeOf(esAdapter)
        val userId = createActiveUser()
        tx.execute { em.persist(ProductLike.of(em.find(Product::class.java, liked.id), userId)) }

        val anonymous = facade.searchProducts(ProductKeywordSearchQuery(keyword = "사료", size = 2), null)
        facade.searchProducts(ProductKeywordSearchQuery(keyword = "사료", size = 2, cursor = checkNotNull(anonymous.nextCursor)), userId)
        val first = facade.searchProducts(ProductKeywordSearchQuery(keyword = "강아지"), userId)

        // 비동기 적재가 늦게 끝나는 잘못된 이벤트도 잡도록 1초 동안 유지되는지 확인
        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(6)).untilAsserted {
            assertEquals(listOf("강아지"), recentKeywords(userId), "커서 페이지와 비로그인 검색은 적재하지 않는다")
        }
        assertEquals(setOf(liked.publicId), first.items.filter { it.isLiked }.mapTo(mutableSetOf()) { it.productId })
        assertTrue(anonymous.items.none { it.isLiked })
    }

    // --- helpers ---

    private fun facadeOf(port: ProductKeywordSearchPort) =
        ProductKeywordSearchFacade(
            ProductKeywordSearchService(port, categoryRepository),
            productLikeStatusService,
            eventPublisher,
            transactionManager,
        )

    private data class Observation(
        val call: String,
        val active: Boolean,
        val readOnly: Boolean,
        val unbound: Boolean,
    )

    /** 포트 호출 시점의 트랜잭션 상태와 스레드에 바인딩된 리소스(EntityManager, 커넥션) 유무를 기록하고 실제 어댑터로 넘김 */
    private class TxProbe(
        private val target: ProductKeywordSearchPort,
    ) : ProductKeywordSearchPort by target {
        val observed = mutableListOf<Observation>()

        override fun search(condition: ProductKeywordSearchCondition): List<ProductCatalogView> {
            record("search")
            return target.search(condition)
        }

        override fun searchFirstPage(condition: ProductKeywordSearchCondition): ProductKeywordSearchFirstPage {
            record("searchFirstPage")
            return target.searchFirstPage(condition)
        }

        override fun findIdByPublicId(publicId: String): Long? {
            record("findIdByPublicId")
            return target.findIdByPublicId(publicId)
        }

        private fun record(call: String) {
            observed +=
                Observation(
                    call = call,
                    active = TransactionSynchronizationManager.isActualTransactionActive(),
                    readOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly(),
                    unbound = TransactionSynchronizationManager.getResourceMap().isEmpty(),
                )
        }
    }

    /** 활성 중분류 하나에 이름에 사료가 든 상품 셋. 앞의 둘은 강아지도 포함 */
    private fun persistFixture(): List<Product> =
        tx.execute {
            em.persist(Seller.open(userId = SELLER, storeName = "store-$SELLER", baseShippingFee = 3000L))
            val root = Category.create(null, 1, "강아지", null, 1)
            em.persist(root)
            val mid = Category.create(root, 2, "사료간식", null, 1)
            em.persist(mid)
            listOf("강아지 사료 1", "강아지 사료 2", "고양이 사료 3").map { name ->
                Product
                    .register(
                        category = mid,
                        sellerId = SELLER,
                        name = name,
                        description = null,
                        representativeImageKey = "products/$name.jpg",
                        regularPrice = 10000L,
                        discountPrice = null,
                        discountStartAt = null,
                        discountEndAt = null,
                    ).also { em.persist(it) }
            }
        }!!

    private fun recentKeywords(userId: Long): List<String> =
        tx.execute {
            em
                .createQuery(
                    "select r.keyword from RecentSearch r where r.userId = :uid order by r.searchedAt desc, r.id desc",
                    String::class.java,
                ).setParameter("uid", userId)
                .resultList
        }!!

    companion object {
        private const val SELLER = 401L
    }
}
