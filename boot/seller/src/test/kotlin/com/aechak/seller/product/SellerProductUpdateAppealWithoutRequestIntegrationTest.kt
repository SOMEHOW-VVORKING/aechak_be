package com.aechak.seller.product

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.file.port.FileKey
import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.application.inspection.service.InspectionRequestService
import com.aechak.application.inspection.usecase.command.RequestProductInspectionCommand
import com.aechak.application.product.product.service.ProductService
import com.aechak.application.product.product.usecase.SellerProductUseCase
import com.aechak.application.product.product.usecase.command.RegisterProductCommand
import com.aechak.application.product.product.usecase.command.UpdateProductCommand
import com.aechak.domain.inspection.ProductReviewQueueItem
import com.aechak.domain.inspection.enums.ReviewQueueStatus
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.product.Product
import com.aechak.domain.seller.seller.Seller
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import java.time.LocalDateTime
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [통합] 검수 **요청 발행 스위치가 꺼진** 환경에서도 상품 수정이 접수된 이의를 무효로 돌리는지 고정한다.
 *
 * 무효화가 요청 발행에 묶여 있던 자리다. 수정 경로가 지문을 요청 발행에서 받아 쓰고 있었고,
 * 요청 서비스는 스위치가 꺼지면 첫 줄에서 null을 돌려준다. 그러면 꺼진 환경에서는 셀러가 문구를
 * 고쳐도 이의가 그대로 남는다.
 *
 * 그 조합이 가정이 아니라 **장애 때의 정상 운용**이다. 요청을 끄는 것이 이 스위치의 용도이고,
 * 끄는 시점에 열린 이의 행은 이미 존재한다. 그 상태에서 관리자는 "셀러는 A에 억울하다 했는데
 * 지금 걸린 것은 B"를 읽고, 셀러는 재접수가 중복으로 막혀 할 수 있는 것이 없다.
 *
 * 내용 지문은 상품명·설명·옵션 이름으로만 계산되므로 스위치와 무관하게 구할 수 있다. 그래서
 * 무효화를 요청 발행 밖으로 뺄 수 있다.
 */
@TestPropertySource(properties = ["inspection.enabled=false"])
class SellerProductUpdateAppealWithoutRequestIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var sellerProductUseCase: SellerProductUseCase

    @Autowired
    private lateinit var inspectionRequestService: InspectionRequestService

    @Autowired
    private lateinit var productService: ProductService

    private var sellerUserId = 0L
    private var leafCategoryId = 0L

    @BeforeEach
    fun seed() {
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
    fun `요청 스위치가 꺼져 있어도 내용을 고치면 접수한 이의가 무효가 된다`() {
        val publicId = register()
        blockedWithAppeal(publicId)

        update(publicId, productName = "연어 건사료 2kg 리뉴얼")

        val row = assertNotNull(findPending(publicId), "행은 열려 있어야 한다. 무효가 되는 것은 이의뿐이다")
        assertNull(row.appealedAt, "고친 뒤의 사유는 지금 걸린 것을 가리키지 않는다")
        assertTrue(row.appealCleared(), "무효가 됐다는 사실이 남지 않으면 접수 실패와 구별되지 않는다")
    }

    /** 반대 방향. 지문이 실제로 맞았는지를 여기서 가른다 — 과하게 비우면 가격 한 번 고치는 것이 이의를 날린다 */
    @Test
    fun `요청 스위치가 꺼져 있고 내용도 그대로면 접수한 이의를 건드리지 않는다`() {
        val publicId = register()
        blockedWithAppeal(publicId)

        update(publicId, regularPrice = 19_900L)

        assertNotNull(findPending(publicId)!!.appealedAt, "본문이 바뀌지 않았으면 이의는 지금 걸린 것을 그대로 가리킨다")
    }

    /**
     * AI가 반려해 열린 행이 있고 셀러가 거기에 이의를 접수한 상태를 심는다.
     *
     * 이의 지문은 지금 저장된 본문으로 계산한다. 발행된 요청에서 읽을 수 없는 환경이라 그렇고,
     * 조성은 요청 서비스가 들고 있는 것을 그대로 쓴다 — 테스트가 따로 조성하면 비교 자체가 거짓이 된다.
     */
    private fun blockedWithAppeal(publicId: String) {
        tx.execute {
            val product =
                em
                    .createQuery("select p from Product p where p.publicId = :pid", Product::class.java)
                    .setParameter("pid", publicId)
                    .singleResult
            val blockingHash =
                inspectionRequestService.productContentHash(
                    RequestProductInspectionCommand(
                        productId = product.id,
                        productVersionNo = 1,
                        productName = product.name,
                        description = product.description,
                        optionNames = productService.optionNamesOf(product.id),
                        categoryPath = emptyList(),
                        imageUrls = emptyList(),
                    ),
                )
            product.rejectByInspection()
            val row =
                ProductReviewQueueItem.openForProduct(
                    product.id,
                    sellerUserId,
                    product.name,
                    LocalDateTime.now().minusDays(1).withNano(0),
                )
            row.appeal("성분 설명입니다.", LocalDateTime.now(), blockingHash)
            em.persist(row)
        }
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
                    regularPrice = 25_000L,
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

    private fun update(
        publicId: String,
        productName: String = "연어 건사료 2kg",
        description: String? = "우리 아이 주식으로 좋습니다.",
        regularPrice: Long = 25_000L,
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

    private fun tmpKey(fileName: String): String = "${FileKey.tmpPrefixOf(sellerUserId, UploadPurpose.PRODUCT)}$fileName"
}
