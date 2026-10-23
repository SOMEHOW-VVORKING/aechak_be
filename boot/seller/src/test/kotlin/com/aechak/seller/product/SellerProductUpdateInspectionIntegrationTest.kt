package com.aechak.seller.product

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.file.port.FileKey
import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.application.messaging.MessagePublisher
import com.aechak.application.product.product.usecase.SellerProductUseCase
import com.aechak.application.product.product.usecase.command.RegisterProductCommand
import com.aechak.application.product.product.usecase.command.UpdateProductCommand
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.enums.InspectionStatus
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
import kotlin.test.assertEquals
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
 * 발행 포트를 기록용으로 바꿔 끼운다. 실제 발행은 아웃박스 테이블에 적는데 그 테이블은
 * 마이그레이션으로만 생겨서, 스키마를 코드로 만드는 이 컨텍스트에는 없다.
 *
 * 노출 게이트도 켠다. 등록 직후 숨기는 것은 요청이 실제로 나갈 때만이라 두 스위치가 함께 켜진 컨텍스트에서만 보인다.
 */
@TestPropertySource(properties = ["inspection.enabled=true", "inspection.gate-enabled=true"])
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
    fun `게이트가 켜져 있으면 등록한 상품을 첫 판정까지 숨긴다`() {
        val publicId = register()

        assertEquals(
            InspectionStatus.PENDING,
            storedInspectionStatus(publicId),
            "판정 전에 노출하면 위반 문구가 AI 판정이 올 때까지 구매자에게 보인다",
        )
    }

    @Test
    fun `아무것도 달라지지 않은 수정은 검수를 요청하지 않는다`() {
        val publicId = register()

        update(publicId)

        assertEquals(1, publisher.productRequests().size, "버전을 남기지 않는 수정은 재검수 대상이 아니다")
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

    private fun storedInspectionStatus(publicId: String): InspectionStatus =
        tx.execute {
            em
                .createQuery("select p.inspectionStatus from Product p where p.publicId = :pid", InspectionStatus::class.java)
                .setParameter("pid", publicId)
                .singleResult
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
