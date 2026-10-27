package com.aechak.seller.product

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.file.port.FileKey
import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.application.messaging.MessagePublisher
import com.aechak.application.product.product.usecase.SellerProductUseCase
import com.aechak.application.product.product.usecase.command.RegisterProductCommand
import com.aechak.application.product.product.usecase.command.UpdateProductCommand
import com.aechak.domain.inspection.ProductReviewQueueItem
import com.aechak.domain.inspection.enums.ReviewQueueStatus
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.product.Product
import com.aechak.domain.seller.seller.Seller
import com.aechak.message.IntegrationMessage
import com.aechak.message.inspection.ProductInspectionRequestedMessage
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.TestPropertySource
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [통합] 상품 수정이 검수를 다시 타는지 고정한다.
 *
 * 이 테스트가 지키는 것은 **등록으로 통과 판정을 받고 수정으로 바꿔치기하는 우회**가 막혀 있다는
 * 사실이다. 노출 게이트를 켜면 그 우회가 게이트를 장식으로 만든다.
 *
 * 함께 지키는 것이 하나 더 있다. 수정 경로의 내용 해시가 등록 경로와 같은 필드 집합으로
 * 계산되어야 한다. 어긋나면 본문이 하나도 바뀌지 않은 상품도 매번 모델을 다시 불러 과금된다.
 *
 * 수정이 접수된 이의를 무효로 돌리는 것도 여기서 본다. 그 무효화의 자리가 수정 경로인 이유는
 * 판정을 기다릴 수 없기 때문이다 — 판정이 실패하거나 기한을 넘기면 그 판정은 검토 큐에 닿지
 * 않아서 비울 기회가 아예 없다.
 *
 * 발행 포트를 기록용으로 바꿔 끼운다. 실제 발행은 아웃박스 테이블에 적는데 그 테이블은
 * 마이그레이션으로만 생겨서, 스키마를 코드로 만드는 이 컨텍스트에는 없다.
 */
@TestPropertySource(properties = ["inspection.enabled=true"])
class SellerProductUpdateInspectionIntegrationTest : IntegrationTestBase() {
    @TestConfiguration
    class RecordingPublisherConfig {
        @Bean
        @Primary
        fun recordingMessagePublisher(): RecordingMessagePublisher = RecordingMessagePublisher()
    }

    class RecordingMessagePublisher : MessagePublisher {
        private val recorded = mutableListOf<IntegrationMessage>()

        override fun publish(message: IntegrationMessage) {
            recorded += message
        }

        fun clear() = recorded.clear()

        fun productRequests(): List<ProductInspectionRequestedMessage> = recorded.filterIsInstance<ProductInspectionRequestedMessage>()
    }

    @Autowired
    private lateinit var sellerProductUseCase: SellerProductUseCase

    @Autowired
    private lateinit var publisher: RecordingMessagePublisher

    private var sellerUserId = 0L
    private var leafCategoryId = 0L

    @BeforeEach
    fun seed() {
        publisher.clear()
        sellerUserId = createActiveUser()
        tx.execute {
            em.persist(Seller.open(sellerUserId, "애착상회", 0L))
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
    fun `등록하면 그룹명과 값 이름이 함께 실린 검수 요청이 나간다`() {
        register()

        val request = publisher.productRequests().single()
        assertEquals(
            listOf("맛", "연어", "오리", "무게", "2kg"),
            request.optionNames,
            "그룹명 뒤에 그 그룹의 값 이름이 입력 순서대로 붙어야 한다",
        )
        assertEquals("연어 건사료 2kg", request.productName)
        assertEquals("우리 아이 주식으로 좋습니다.", request.description)
    }

    @Test
    fun `상품명을 바꾸면 새 검수 요청이 나가고 옵션 이름은 등록 때와 같다`() {
        val publicId = register()
        val registered = publisher.productRequests().single()

        update(publicId, productName = "연어 건사료 2kg 최저가 직거래 환영")

        val requests = publisher.productRequests()
        assertEquals(2, requests.size, "수정이 검수를 타지 않으면 등록으로 통과 판정만 받고 바꿔치기할 수 있다")
        val updated = requests.last()
        assertEquals("연어 건사료 2kg 최저가 직거래 환영", updated.productName)
        assertEquals(
            registered.optionNames,
            updated.optionNames,
            "수정 명령에는 옵션이 없어 저장된 값을 읽는다 — 순서까지 등록 때와 같아야 해시가 맞는다",
        )
        assertTrue(updated.contentHash != registered.contentHash, "본문이 달라졌으니 해시도 달라야 한다")
        assertEquals(2, inspectionCount(), "새 내용이므로 검수 행도 새로 생겨야 한다")
    }

    @Test
    fun `가격만 바꾼 수정은 버전을 남기지만 같은 판정을 재사용한다`() {
        val publicId = register()

        update(publicId, regularPrice = 19900L)

        assertEquals(
            1,
            publisher.productRequests().size,
            "판정 대상 본문이 그대로면 해시가 같아 기존 검수를 재사용하고 모델을 다시 부르지 않는다",
        )
        assertEquals(1, inspectionCount())
    }

    @Test
    fun `아무것도 달라지지 않은 수정은 검수를 요청하지 않는다`() {
        val publicId = register()

        update(publicId)

        assertEquals(1, publisher.productRequests().size, "버전을 남기지 않는 수정은 재검수 대상이 아니다")
    }

    /**
     * 판정이 오지 않아도 내용이 바뀌면 이의는 무효가 된다.
     *
     * 접수 사유는 지금 걸려 있는 것에 대고 쓴 문장이다. 셀러가 내용을 고치면 그 문장이 가리키는
     * 것이 없어지는데, 판정 쪽에서만 비우면 그 판정이 안 오는 경우를 덮지 못한다 — 모델이
     * 죽거나 기한을 넘기면 상품도 큐도 건드리지 않으므로 비울 자리가 없다. 그러면 관리자는
     * "셀러는 A에 억울하다 했는데 지금 걸린 것은 B"를 읽고, 셀러는 재접수가 중복으로 막혀
     * 할 수 있는 것이 없고, 그 행이 셀러 정원도 계속 먹는다.
     */
    @Test
    fun `판정이 오지 않아도 내용을 고치면 접수한 이의가 무효가 된다`() {
        val publicId = register()
        val blindedAt = blockedWithAppeal(publicId)

        update(publicId, productName = "연어 건사료 2kg 리뉴얼")

        val row = assertNotNull(findPending(publicId), "행은 열려 있어야 한다. 무효가 되는 것은 이의뿐이다")
        assertNull(row.appealedAt, "고친 뒤의 사유는 지금 걸린 것을 가리키지 않는다")
        assertNull(row.appealReason)
        assertTrue(row.appealCleared(), "무효가 됐다는 사실이 남지 않으면 접수 실패와 구별되지 않는다")
        assertEquals(blindedAt, row.blindedAt, "관리자 목록의 정렬 키를 셀러 수정이 밀면 수정 한 번이 큐 정렬 조작이 된다")
    }

    /** 판정 대상 본문이 그대로면 셀러가 적은 사유도 그대로 가리킨다. 지우면 가격 한 번 고치는 것이 이의를 날린다 */
    @Test
    fun `내용이 그대로인 수정은 접수한 이의를 건드리지 않는다`() {
        val publicId = register()
        blockedWithAppeal(publicId)

        update(publicId, regularPrice = 19900L)

        assertNotNull(findPending(publicId)!!.appealedAt, "본문이 바뀌지 않았으면 이의는 지금 걸린 것을 그대로 가리킨다")
    }

    /**
     * AI가 반려해 열린 행이 생기고 셀러가 거기에 이의를 접수한 상태를 심는다.
     *
     * 판정 적용이 실제로 만드는 조합이다 — 위반 판정은 상품을 반려하고 같은 트랜잭션에서 행을
     * 연다. 이 모듈에는 판정 수신 경로가 없어서 그 결과 상태를 심는다. 이의 지문은 발행된
     * 검수 요청에서 읽는다. 접수 경로가 쓰는 것과 같은 값이어야 비교가 성립한다.
     */
    private fun blockedWithAppeal(publicId: String): LocalDateTime {
        val blindedAt = LocalDateTime.now().minusDays(1).withNano(0)
        val blockingHash = publisher.productRequests().last().contentHash
        tx.execute {
            val product =
                em
                    .createQuery("select p from Product p where p.publicId = :pid", Product::class.java)
                    .setParameter("pid", publicId)
                    .singleResult
            product.rejectByInspection()
            val row = ProductReviewQueueItem.openForProduct(product.id, sellerUserId, "연어 건사료 2kg", blindedAt)
            row.appeal("성분 설명입니다.", LocalDateTime.now(), blockingHash)
            em.persist(row)
        }
        return blindedAt
    }

    /** 열린 행. 둘 이상이면 바로 깨뜨린다 — 조용히 하나만 고르면 중복이 보이지 않는다 */
    private fun findPending(publicId: String): ProductReviewQueueItem? =
        tx.execute {
            val rows =
                em
                    .createQuery(
                        "select i from ProductReviewQueueItem i, Product p " +
                            "where p.publicId = :pid and i.targetId = p.id and i.status = :st",
                        ProductReviewQueueItem::class.java,
                    ).setParameter("pid", publicId)
                    .setParameter("st", ReviewQueueStatus.PENDING_REVIEW)
                    .resultList
            assertTrue(rows.size <= 1, "상품마다 열린 행은 하나여야 한다. 실제=${rows.size}")
            rows.firstOrNull()
        }

    private fun register(): String =
        sellerProductUseCase
            .registerProduct(
                RegisterProductCommand(
                    sellerId = sellerUserId,
                    categoryId = leafCategoryId,
                    productName = "연어 건사료 2kg",
                    description = "우리 아이 주식으로 좋습니다.",
                    regularPrice = 25000L,
                    discountPrice = null,
                    discountStartAt = null,
                    discountEndAt = null,
                    thumbnailImageKey = tmpKey("thumbnail.png"),
                    additionalImageKeys = emptyList(),
                    detailImageKeys = emptyList(),
                    optionGroups =
                        listOf(
                            RegisterProductCommand.OptionGroupCommand("맛", listOf("연어", "오리")),
                            RegisterProductCommand.OptionGroupCommand("무게", listOf("2kg")),
                        ),
                    optionCombinations =
                        listOf(
                            RegisterProductCommand.OptionCombinationCommand(listOf("연어", "2kg"), 0L, 50),
                            RegisterProductCommand.OptionCombinationCommand(listOf("오리", "2kg"), 0L, 50),
                        ),
                ),
            ).publicId

    /**
     * 기본값은 등록과 같은 값이다. 이미지 키는 저장된 것을 그대로 넘겨야 바뀌지 않은 것으로 읽힌다 —
     * 등록 때 넘긴 임시 키는 승격되어 다른 값으로 저장되기 때문이다.
     */
    private fun update(
        publicId: String,
        productName: String = "연어 건사료 2kg",
        description: String? = "우리 아이 주식으로 좋습니다.",
        regularPrice: Long = 25000L,
    ) {
        sellerProductUseCase.updateProduct(
            UpdateProductCommand(
                sellerId = sellerUserId,
                productPublicId = publicId,
                categoryId = leafCategoryId,
                productName = productName,
                description = description,
                regularPrice = regularPrice,
                discountPrice = null,
                discountStartAt = null,
                discountEndAt = null,
                thumbnailImageKey = storedRepresentativeKey(publicId),
                additionalImageKeys = emptyList(),
                detailImageKeys = emptyList(),
            ),
        )
    }

    private fun storedRepresentativeKey(publicId: String): String =
        tx.execute {
            em
                .createQuery("select p from Product p where p.publicId = :pid", Product::class.java)
                .setParameter("pid", publicId)
                .singleResult
                .representativeImageKey!!
        }!!

    private fun inspectionCount(): Int =
        tx.execute {
            em
                .createQuery("select count(i) from ContentInspection i", Long::class.javaObjectType)
                .singleResult
                .toInt()
        }!!

    private fun tmpKey(fileName: String): String = "${FileKey.tmpPrefixOf(sellerUserId, UploadPurpose.PRODUCT)}$fileName"
}
