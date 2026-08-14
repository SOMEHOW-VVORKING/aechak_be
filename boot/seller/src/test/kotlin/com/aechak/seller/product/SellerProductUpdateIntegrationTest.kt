package com.aechak.seller.product

import com.aechak.api.support.FakeFileStorage
import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.file.port.FileKey
import com.aechak.application.file.port.FileStorage
import com.aechak.application.file.port.enums.UploadPurpose
import com.aechak.application.product.usecase.ProductUseCase
import com.aechak.application.product.usecase.query.ProductSearchQuery
import com.aechak.common.error.BusinessException
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.OrderItem
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.category.enums.CategoryStatus
import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.enums.ProductImageType
import com.aechak.domain.seller.seller.Seller
import com.aechak.domain.seller.seller.enums.SellerStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.LocalDateTime

/**
 * 셀러 상품 정보 수정 통합 테스트. 상태코드와 에러코드, 구매자 화면 반영, 이미지 전체 교체를 고정한다.
 * 수정해도 과거 주문이 보던 상품명과 정가가 흔들리지 않는다는 이 기능의 핵심 불변식도 여기서 본다.
 */
class SellerProductUpdateIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    @Autowired
    private lateinit var productUseCase: ProductUseCase

    @Autowired
    private lateinit var fileStorage: FileStorage

    private lateinit var mockMvc: MockMvc
    private var sellerUserId = 0L
    private lateinit var token: String
    private var midCategoryId = 0L
    private var leafCategoryId = 0L
    private var otherLeafCategoryId = 0L
    private lateinit var productId: String

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
        sellerUserId = createActiveUser()
        token = mintAccessToken(sellerUserId)
        openSeller(sellerUserId)
        tx.execute {
            val root = Category.create(null, Category.ROOT_DEPTH, "강아지", null, 1)
            em.persist(root)
            val mid = Category.create(root, Category.MID_DEPTH, "사료", null, 1)
            em.persist(mid)
            val leaf = Category.create(mid, Category.LEAF_DEPTH, "건사료", null, 1)
            em.persist(leaf)
            val otherLeaf = Category.create(mid, Category.LEAF_DEPTH, "습식사료", null, 2)
            em.persist(otherLeaf)
            midCategoryId = mid.id
            leafCategoryId = leaf.id
            otherLeafCategoryId = otherLeaf.id
        }
        productId = registerProduct(token)
    }

    @Test
    fun `정상 수정은 200과 수정된 상품 요약을 반환한다`() {
        mockMvc
            .perform(updateRequest(token, productId, updateJson(productName = "닭가슴살 큐브", regularPrice = 30_000L)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.productId").value(productId))
            .andExpect(jsonPath("$.data.saleStatus").value("ON_SALE"))
            .andExpect(jsonPath("$.data.updatedAt").exists())
    }

    @Test
    fun `수정한 상품명과 정가가 구매자 상세에 바로 반영된다`() {
        mockMvc
            .perform(updateRequest(token, productId, updateJson(productName = "닭가슴살 큐브", regularPrice = 30_000L)))
            .andExpect(status().isOk)

        val detail = productUseCase.getProduct(productId, null)
        assertEquals("닭가슴살 큐브", detail.name, "구매자 상세가 수정된 상품명을 보여야 한다")
        assertEquals(30_000L, detail.regularPrice, "구매자 상세가 수정된 정가를 보여야 한다")
    }

    @Test
    fun `응답의 updatedAt은 갱신 후 시각이다`() {
        val body =
            mockMvc
                .perform(updateRequest(token, productId, updateJson(regularPrice = 30_000L)))
                .andExpect(status().isOk)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)

        val responseUpdatedAt = LocalDateTime.parse(Regex("\"updatedAt\":\"([^\"]+)\"").find(body)!!.groupValues[1])
        val createdAt =
            tx.execute { em.createQuery("select p.createdAt from Product p", LocalDateTime::class.java).singleResult }!!
        assertTrue(
            responseUpdatedAt.isAfter(createdAt),
            "갱신 전 값을 실으면 등록 시각 그대로 나간다. 응답: $responseUpdatedAt, 등록: $createdAt",
        )
    }

    @Test
    fun `카테고리도 함께 바뀐다`() {
        mockMvc
            .perform(updateRequest(token, productId, updateJson(categoryId = otherLeafCategoryId)))
            .andExpect(status().isOk)

        assertEquals(
            otherLeafCategoryId,
            tx.execute { em.createQuery("select p.category.id from Product p", Long::class.javaObjectType).singleResult },
            "수정 요청의 소분류로 옮겨져야 한다",
        )
    }

    @Test
    fun `이미지는 전체 교체되고 빠진 키는 사라진다`() {
        mockMvc
            .perform(
                updateRequest(
                    token,
                    productId,
                    updateJson(
                        thumbnailImageKey = "${tmpKey("new-thumb.png")}",
                        additionalImageKeys = listOf(tmpKey("a9.png")),
                        detailImageKeys = emptyList(),
                    ),
                ),
            ).andExpect(status().isOk)

        assertEquals(
            listOf(
                ProductImageType.REPRESENTATIVE to "products/new-thumb.png",
                ProductImageType.PRODUCT to "products/a9.png",
            ),
            imageRows(),
            "보낸 목록이 곧 결과여야 한다: ${imageRows()}",
        )
    }

    @Test
    fun `새로 올린 이미지는 정식 경로로 옮겨져 저장된다`() {
        mockMvc
            .perform(updateRequest(token, productId, updateJson(additionalImageKeys = listOf(tmpKey("a9.png")))))
            .andExpect(status().isOk)

        assertTrue(
            imageRows().any { it == ProductImageType.PRODUCT to "products/a9.png" },
            "tmp 키가 아니라 승격된 키가 저장돼야 한다: ${imageRows()}",
        )
    }

    @Test
    fun `이미 저장돼 있던 키는 다시 승격하지 않는다`() {
        mockMvc
            .perform(updateRequest(token, productId, updateJson(productName = "닭가슴살 큐브")))
            .andExpect(status().isOk)

        assertEquals(
            listOf(
                ProductImageType.REPRESENTATIVE to "products/thumbnail.png",
                ProductImageType.PRODUCT to "products/a1.png",
                ProductImageType.DETAIL to "products/d1.png",
            ),
            imageRows(),
            "정식 키를 그대로 보내면 승격을 건너뛰고 값이 유지돼야 한다: ${imageRows()}",
        )
    }

    @Test
    fun `타인이 업로드한 이미지 키는 100002를 반환한다`() {
        val other = createActiveUser()
        mockMvc
            .perform(
                updateRequest(token, productId, updateJson(detailImageKeys = listOf(tmpKey("d1.png", ownerId = other)))),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(100002))
    }

    @Test
    fun `발급 용도가 다른 이미지 키는 100003을 반환한다`() {
        assertRejected(
            updateJson(detailImageKeys = listOf(tmpKey("d1.png", purpose = UploadPurpose.USER_PROFILE))),
            100003,
        )
    }

    @Test
    fun `없는 상품이면 승격 전에 404로 끊긴다`() {
        mockMvc
            .perform(
                updateRequest(token, "01JZZZZZZZZZZZZZZZZZZZZZZZ", updateJson(additionalImageKeys = listOf(tmpKey("a9.png")))),
            ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(40000))
    }

    @Test
    fun `거절되는 요청은 스토리지에 사본을 남기지 않는다`() {
        clearPromoted()

        assertRejected(updateJson(regularPrice = 0L, additionalImageKeys = listOf(tmpKey("a9.png"))), 40001)
        assertRejected(updateJson(categoryId = midCategoryId, additionalImageKeys = listOf(tmpKey("a9.png"))), 40100)
        assertRejected(
            updateJson(additionalImageKeys = (1..Product.ADDITIONAL_IMAGE_MAX + 1).map { tmpKey("a$it.png") }),
            40003,
        )

        assertTrue(
            promotedKeys().isEmpty(),
            "승격은 되돌릴 수 없어서 거절 전에 일어나면 안 된다. 남은 키: ${promotedKeys()}",
        )
    }

    @Test
    fun `같은 이미지 키를 한 종류 안에 두 번 넣으면 90001이다`() {
        assertRejected(updateJson(additionalImageKeys = listOf("products/a1.png", "products/a1.png")), 90001)
        assertRejected(updateJson(detailImageKeys = listOf("products/d1.png", "products/d1.png")), 90001)
    }

    @Test
    fun `종류가 다르면 같은 키를 함께 보낼 수 있다`() {
        mockMvc
            .perform(
                updateRequest(
                    token,
                    productId,
                    updateJson(additionalImageKeys = listOf("products/a1.png"), detailImageKeys = listOf("products/a1.png")),
                ),
            ).andExpect(status().isOk)

        assertEquals(
            listOf(ProductImageType.PRODUCT, ProductImageType.DETAIL),
            imageRows().filter { it.second == "products/a1.png" }.map { it.first },
            "같은 파일을 추가와 상세에 함께 걸 수 있어야 한다: ${imageRows()}",
        )
    }

    @Test
    fun `등록부터 수정과 상태 변경을 거쳐 구매자 조회까지 관통한다`() {
        mockMvc
            .perform(updateRequest(token, productId, updateJson(productName = "닭가슴살 큐브", regularPrice = 30_000L)))
            .andExpect(status().isOk)

        mockMvc
            .perform(
                patch("/api/v1/sellers/me/products/$productId/status")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"saleStatus": "SUSPENDED"}"""),
            ).andExpect(status().isOk)
        assertEquals(listOf(1, 2), versionNos(), "등록 1, 정보 수정 2. 상태 변경은 버전을 안 남김: ${versionNos()}")

        val thrown = assertThrows<BusinessException> { productUseCase.getProduct(productId, null) }
        assertEquals(40000, thrown.errorCode.code, "판매중지 상품은 구매자 상세에서 40000이어야 한다")

        mockMvc
            .perform(
                patch("/api/v1/sellers/me/products/$productId/status")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"saleStatus": "ON_SALE"}"""),
            ).andExpect(status().isOk)

        val detail = productUseCase.getProduct(productId, null)
        assertEquals("닭가슴살 큐브", detail.name, "재판매 복귀 후 구매자 상세가 수정된 상품명을 보여야 한다")
        assertEquals(30_000L, detail.regularPrice, "재판매 복귀 후 구매자 상세가 수정된 정가를 보여야 한다")
    }

    @Test
    fun `한 번 뺀 이미지 키를 다시 보내면 100002로 거절된다`() {
        // 재노출은 재업로드가 실제 경로다. 닫힌 키는 tmp 접두가 없어 승격 소유 검증에 걸린다.
        mockMvc
            .perform(updateRequest(token, productId, updateJson(additionalImageKeys = emptyList())))
            .andExpect(status().isOk)

        mockMvc
            .perform(updateRequest(token, productId, updateJson(additionalImageKeys = listOf("products/a1.png"))))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(100002))
    }

    @Test
    fun `구매자 상세에는 닫힌 이미지가 보이지 않는다`() {
        mockMvc
            .perform(
                updateRequest(
                    token,
                    productId,
                    updateJson(
                        thumbnailImageKey = "${tmpKey("new-thumb.png")}",
                        additionalImageKeys = emptyList(),
                        detailImageKeys = emptyList(),
                    ),
                ),
            ).andExpect(status().isOk)

        assertEquals(
            listOf("products/new-thumb.png"),
            productUseCase.getProduct(productId, null).images.map { it.storageKey },
            "닫힌 구간의 행이 구매자 상세에 섞이면 안 된다",
        )
    }

    @Test
    fun `교체로 빠진 이미지는 어느 버전까지 쓰였는지 남는다`() {
        mockMvc
            .perform(
                updateRequest(
                    token,
                    productId,
                    updateJson(
                        thumbnailImageKey = "${tmpKey("new-thumb.png")}",
                        additionalImageKeys = emptyList(),
                        detailImageKeys = emptyList(),
                    ),
                ),
            ).andExpect(status().isOk)

        val closed =
            tx.execute {
                em
                    .createQuery(
                        "select i.storageKey, i.fromVersionNo, i.toVersionNo from Product p join p._images i " +
                            "where i.toVersionNo is not null order by i.id asc",
                        Array<Any>::class.java,
                    ).resultList
                    .map { it.toList() }
            }!!
        assertEquals(
            listOf(
                listOf("products/thumbnail.png", 1, 1),
                listOf("products/a1.png", 1, 1),
                listOf("products/d1.png", 1, 1),
            ),
            closed,
            "빠진 행은 등록 버전 1 구간으로 닫힌 채 남아야 한다: $closed",
        )
    }

    @Test
    fun `값이 하나도 안 달라지면 버전을 남기지 않는다`() {
        mockMvc
            .perform(updateRequest(token, productId, updateJson()))
            .andExpect(status().isOk)

        assertEquals(1L, countOf("ProductVersion"), "저장 버튼을 다시 눌렀다고 이력이 쌓이면 안 된다")
    }

    @Test
    fun `이미지 순서만 바꾸면 버전을 남기지 않는다`() {
        mockMvc
            .perform(
                updateRequest(token, productId, updateJson(additionalImageKeys = listOf("products/a1.png", tmpKey("a2.png")))),
            ).andExpect(status().isOk)
        val rowsBeforeReorder = countOf("ProductImage")

        mockMvc
            .perform(
                updateRequest(token, productId, updateJson(additionalImageKeys = listOf("products/a2.png", "products/a1.png"))),
            ).andExpect(status().isOk)

        assertEquals(2L, countOf("ProductVersion"), "순서만 바꾼 저장은 이력을 남기면 안 된다")
        assertEquals(rowsBeforeReorder, countOf("ProductImage"), "순서만 바꿨는데 행이 늘면 안 된다")
        assertEquals(
            listOf("products/a2.png" to 0, "products/a1.png" to 1),
            additionalImageOrders(),
            "순서는 보낸 대로 바뀌어 있어야 한다",
        )
    }

    @Test
    fun `값이 달라질 때마다 버전이 하나씩 쌓인다`() {
        mockMvc.perform(updateRequest(token, productId, updateJson(regularPrice = 30_000L))).andExpect(status().isOk)
        mockMvc
            .perform(updateRequest(token, productId, updateJson(productName = "닭가슴살 큐브", regularPrice = 30_000L)))
            .andExpect(status().isOk)

        assertEquals(listOf(1, 2, 3), versionNos(), "등록 1, 정가 수정 2, 상품명 수정 3으로 이어져야 한다: ${versionNos()}")
    }

    @Test
    fun `할인만 바꿔도 버전이 남는다`() {
        mockMvc
            .perform(
                updateRequest(
                    token,
                    productId,
                    updateJson(discountPrice = 20_000L, discountStartAt = "2026-08-11T10:00:00"),
                ),
            ).andExpect(status().isOk)

        assertEquals(2L, countOf("ProductVersion"), "정가가 그대로여도 할인이 바뀌었으면 이력이 남아야 한다")
    }

    @Test
    fun `할인가만 바꿔도 버전이 남는다`() {
        applyDiscount(20_000L, DISCOUNT_START, DISCOUNT_END, expectedVersionNo = 2)

        applyDiscount(18_000L, DISCOUNT_START, DISCOUNT_END, expectedVersionNo = 3)
    }

    @Test
    fun `할인 시작일만 바꿔도 버전이 남는다`() {
        applyDiscount(20_000L, DISCOUNT_START, DISCOUNT_END, expectedVersionNo = 2)

        applyDiscount(20_000L, "2026-08-12T10:00:00", DISCOUNT_END, expectedVersionNo = 3)
    }

    @Test
    fun `할인 종료일만 바꿔도 버전이 남는다`() {
        applyDiscount(20_000L, DISCOUNT_START, DISCOUNT_END, expectedVersionNo = 2)

        applyDiscount(20_000L, DISCOUNT_START, "2026-08-21T10:00:00", expectedVersionNo = 3)
    }

    @Test
    fun `카테고리만 바꿔도 버전이 남는다`() {
        mockMvc
            .perform(updateRequest(token, productId, updateJson(categoryId = otherLeafCategoryId)))
            .andExpect(status().isOk)

        assertEquals(listOf(1, 2), versionNos(), "카테고리 변경도 이력이 남아야 한다: ${versionNos()}")
    }

    @Test
    fun `이미지만 바꿔도 버전이 남는다`() {
        mockMvc
            .perform(updateRequest(token, productId, updateJson(additionalImageKeys = listOf(tmpKey("a9.png")))))
            .andExpect(status().isOk)

        assertEquals(listOf(1, 2), versionNos(), "이미지 변경도 이력이 남아야 한다: ${versionNos()}")
    }

    @Test
    fun `설명만 바꿔도 저장되고 버전이 남는다`() {
        mockMvc
            .perform(updateRequest(token, productId, updateJson(description = "닭가슴살 100%")))
            .andExpect(status().isOk)

        assertEquals(listOf(1, 2), versionNos(), "설명 변경도 이력이 남아야 한다: ${versionNos()}")

        assertEquals(
            "닭가슴살 100%",
            tx.execute { em.createQuery("select p.description from Product p", String::class.java).singleResult },
            "설명이 저장돼야 한다",
        )
    }

    @Test
    fun `설명을 비우면 null로 지워진다`() {
        mockMvc
            .perform(updateRequest(token, productId, updateJson(description = null)))
            .andExpect(status().isOk)

        assertNull(
            tx.execute { em.createQuery("select p.description from Product p", String::class.java).resultList.firstOrNull() },
            "설명은 null로 지울 수 있어야 한다",
        )
    }

    @Test
    fun `수정해도 과거 주문이 보던 상품명과 정가는 그대로다`() {
        val versionId = tx.execute { firstVersionId() }!!
        placeOrder(productVersionId = versionId, unitPriceSnapshot = 25_000L)

        mockMvc
            .perform(updateRequest(token, productId, updateJson(productName = "닭가슴살 큐브", regularPrice = 30_000L)))
            .andExpect(status().isOk)

        val purchased =
            tx.execute {
                em
                    .createQuery(
                        "select v.nameSnapshot, v.priceSnapshot from ProductVersion v where v.id = :id",
                        Array<Any>::class.java,
                    ).setParameter("id", orderedVersionId())
                    .singleResult
            }!!
        assertEquals("연어 건사료 2kg", purchased[0], "주문이 가리키는 버전의 상품명이 결제 당시 값이어야 한다")
        assertEquals(25_000L, purchased[1], "주문이 가리키는 버전의 정가가 결제 당시 값이어야 한다")
        assertEquals(versionId, tx.execute { orderedVersionId() }, "주문이 가리키는 버전 행 자체가 바뀌면 안 된다")
    }

    @Test
    fun `남의 상품 수정은 403이 아니라 404다`() {
        val otherSeller = createActiveUser()
        openSeller(otherSeller)

        mockMvc
            .perform(updateRequest(mintAccessToken(otherSeller), productId, updateJson()))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(40000))
    }

    @Test
    fun `없는 상품 수정은 40000을 반환한다`() {
        mockMvc
            .perform(updateRequest(token, "01JZZZZZZZZZZZZZZZZZZZZZZZ", updateJson()))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(40000))
    }

    @Test
    fun `남의 상품 수정 시도는 그 상품을 건드리지 않는다`() {
        val otherSeller = createActiveUser()
        openSeller(otherSeller)

        mockMvc
            .perform(updateRequest(mintAccessToken(otherSeller), productId, updateJson(productName = "가로챈 이름")))
            .andExpect(status().isNotFound)

        assertEquals(
            "연어 건사료 2kg",
            tx.execute { em.createQuery("select p.name from Product p", String::class.java).singleResult },
            "거절된 요청이 상품명을 바꾸면 안 된다",
        )
    }

    @Test
    fun `비활성 카테고리로 옮기면 40101을 반환한다`() {
        tx.execute {
            em
                .createQuery("update Category c set c.status = :st where c.id = :id")
                .setParameter("st", CategoryStatus.INACTIVE)
                .setParameter("id", otherLeafCategoryId)
                .executeUpdate()
        }
        mockMvc
            .perform(updateRequest(token, productId, updateJson(categoryId = otherLeafCategoryId)))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(40101))
    }

    @Test
    fun `중분류를 지정하면 40100을 반환한다`() {
        assertRejected(updateJson(categoryId = midCategoryId), 40100)
    }

    @Test
    fun `이미지가 종류별 상한을 넘으면 40003을 반환한다`() {
        assertRejected(updateJson(detailImageKeys = (1..21).map { tmpKey("d$it.png") }), 40003)
    }

    @Test
    fun `정가가 허용 범위를 벗어나면 40001을 반환한다`() {
        assertRejected(updateJson(regularPrice = 99L), 40001)
        assertRejected(updateJson(regularPrice = 100_000_001L), 40001)
    }

    @Test
    fun `할인가가 정가를 넘으면 40001을 반환한다`() {
        assertRejected(updateJson(discountPrice = 30_000L, discountStartAt = "2026-08-11T10:00:00"), 40001)
    }

    @Test
    fun `할인가에 시작일이 없으면 40002를 반환한다`() {
        assertRejected(updateJson(discountPrice = 20_000L), 40002)
    }

    @Test
    fun `할인 기간이 역전되면 40002를 반환한다`() {
        assertRejected(
            updateJson(
                discountPrice = 20_000L,
                discountStartAt = "2026-08-11T10:00:00",
                discountEndAt = "2026-08-11T09:00:00",
            ),
            40002,
        )
    }

    @Test
    fun `할인가 없이 기간만 지정하면 40002를 반환한다`() {
        assertRejected(updateJson(discountStartAt = "2026-08-11T10:00:00"), 40002)
    }

    @Test
    fun `거절된 수정은 버전을 남기지 않는다`() {
        assertRejected(updateJson(categoryId = midCategoryId), 40100)

        assertEquals(1L, countOf("ProductVersion"), "롤백되면 등록 승인본 1행만 남아야 한다")
    }

    @Test
    fun `상품명이 비어 있으면 90001을 반환한다`() {
        assertRejected(updateJson(productName = ""), 90001)
    }

    @Test
    fun `필수 필드를 아예 빼면 본문 파싱 단계에서 90001을 반환한다`() {
        assertRejected("""{"categoryId": $leafCategoryId, "regularPrice": 25000}""", 90001)
    }

    @Test
    fun `셀러가 아닌 계정은 40004를 반환한다`() {
        val notSeller = createActiveUser()
        mockMvc
            .perform(updateRequest(mintAccessToken(notSeller), productId, updateJson()))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(40004))
    }

    @Test
    fun `정지된 셀러는 40004를 반환한다`() {
        tx.execute {
            em
                .createQuery("update Seller s set s.status = :st where s.userId = :id")
                .setParameter("st", SellerStatus.SUSPENDED)
                .setParameter("id", sellerUserId)
                .executeUpdate()
        }
        mockMvc
            .perform(updateRequest(token, productId, updateJson()))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(40004))
    }

    @Test
    fun `옵션은 이 요청으로 바뀌지 않는다`() {
        mockMvc
            .perform(updateRequest(token, productId, updateJson(extra = """"optionCombinations": [], "optionGroups": [],""")))
            .andExpect(status().isOk)

        assertEquals(1L, countOf("OptionCombination"), "계약에 없는 옵션 필드를 실어도 조합이 지워지면 안 된다")
    }

    @Test
    fun `판매 상태는 이 요청으로 바뀌지 않는다`() {
        mockMvc
            .perform(updateRequest(token, productId, updateJson(extra = """"saleStatus": "SUSPENDED",""")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.saleStatus").value("ON_SALE"))

        assertTrue(
            productUseCase.getProducts(ProductSearchQuery()).items.any { it.productId == productId },
            "정보 수정이 노출 상태를 건드리면 안 된다",
        )
    }

    @Test
    fun `미로그인 수정은 401과 20004다`() {
        mockMvc
            .perform(
                put("/api/v1/sellers/me/products/$productId")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateJson()),
            ).andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value(20004))
    }

    private fun assertRejected(
        body: String,
        errorCode: Int,
        expectedStatus: Int = 400,
    ) {
        val result =
            mockMvc
                .perform(updateRequest(token, productId, body))
                .andExpect(jsonPath("$.errorCode").value(errorCode))
                .andReturn()
        assertEquals(expectedStatus, result.response.status, "$errorCode 는 $expectedStatus 로 나가야 한다")
    }

    /** 공통 업로드가 발급하는 tmp 키 형태. 승격이 소유자와 용도를 이 접두로 가른다. */
    private fun tmpKey(
        fileName: String,
        ownerId: Long = sellerUserId,
        purpose: UploadPurpose = UploadPurpose.PRODUCT,
    ): String = "${FileKey.tmpPrefixOf(ownerId, purpose)}$fileName"

    private fun openSeller(userId: Long) {
        tx.execute { em.persist(Seller.open(userId, "애착상회$userId", 0L)) }
    }

    private fun placeOrder(
        productVersionId: Long,
        unitPriceSnapshot: Long,
    ) {
        tx.execute {
            val group = OrderGroup.create(9_999L, 0L, unitPriceSnapshot, 0L, unitPriceSnapshot, "idem-$productVersionId")
            em.persist(group)
            val item =
                OrderItem.of(
                    productId = internalProductId(),
                    optionCombinationId = combinationId(),
                    quantity = 1,
                    unitPriceSnapshot = unitPriceSnapshot,
                    discountAllocatedAmount = 0L,
                    productVersionId = productVersionId,
                )
            em.persist(Order.place(group, sellerUserId, 0L, 0L, listOf(item)))
        }
    }

    private fun internalProductId(): Long =
        em
            .createQuery("select p.id from Product p where p.publicId = :publicId", Long::class.javaObjectType)
            .setParameter("publicId", productId)
            .singleResult

    private fun combinationId(): Long =
        em.createQuery("select c.id from OptionCombination c", Long::class.javaObjectType).resultList.first()

    private fun firstVersionId(): Long =
        em
            .createQuery("select v.id from ProductVersion v order by v.versionNo asc", Long::class.javaObjectType)
            .resultList
            .first()

    private fun orderedVersionId(): Long =
        tx.execute {
            em.createQuery("select i.productVersionId from OrderItem i", Long::class.javaObjectType).singleResult
        }!!

    private fun versionNos(): List<Int> =
        tx.execute {
            em
                .createQuery("select v.versionNo from ProductVersion v order by v.versionNo asc", Int::class.javaObjectType)
                .resultList
        }!!

    private fun imageRows(): List<Pair<ProductImageType, String>> =
        tx.execute {
            em
                .createQuery(
                    "select i.imageType, i.storageKey from Product p join p._images i where i.toVersionNo is null order by i.id asc",
                    Array<Any>::class.java,
                ).resultList
                .map { it[0] as ProductImageType to it[1] as String }
        }!!

    /** 현재 노출 중인 추가 이미지를 키와 순서 쌍으로 돌려줌. 순서 갱신이 행에 실제로 닿았는지 보는 자리. */
    private fun additionalImageOrders(): List<Pair<String, Int>> =
        tx.execute {
            em
                .createQuery(
                    "select i.storageKey, i.sortOrder from Product p join p._images i " +
                        "where i.imageType = :type and i.toVersionNo is null order by i.sortOrder asc",
                    Array<Any>::class.java,
                ).setParameter("type", ProductImageType.PRODUCT)
                .resultList
                .map { it[0] as String to it[1] as Int }
        }!!

    private fun promotedKeys(): List<String> = (fileStorage as FakeFileStorage).promotedKeys

    private fun clearPromoted() = (fileStorage as FakeFileStorage).clearPromoted()

    private fun countOf(entityName: String): Long =
        tx.execute {
            em.createQuery("select count(e) from $entityName e", Long::class.javaObjectType).singleResult
        }!!

    private fun registerProduct(token: String): String {
        val body =
            mockMvc
                .perform(
                    post("/api/v1/sellers/me/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """
                            {
                              "categoryId": $leafCategoryId,
                              "productName": "연어 건사료 2kg",
                              "description": "연어 90% 함유",
                              "regularPrice": 25000,
                              "thumbnailImageKey": "${tmpKey("thumbnail.png")}",
                              "additionalImageKeys": ["${tmpKey("a1.png")}"],
                              "detailImageKeys": ["${tmpKey("d1.png")}"],
                              "optionCombinations": [{"optionValues": [], "additionalPrice": 0, "stockQuantity": 50}]
                            }
                            """.trimIndent(),
                        ),
                ).andExpect(status().isCreated)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)
        return Regex("\"productId\":\"([^\"]+)\"").find(body)!!.groupValues[1]
    }

    private fun updateRequest(
        token: String,
        productId: String,
        body: String,
    ): MockHttpServletRequestBuilder =
        put("/api/v1/sellers/me/products/$productId")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)

    private fun updateJson(
        categoryId: Long = leafCategoryId,
        productName: String = "연어 건사료 2kg",
        description: String? = "연어 90% 함유",
        regularPrice: Long = 25_000L,
        discountPrice: Long? = null,
        discountStartAt: String? = null,
        discountEndAt: String? = null,
        thumbnailImageKey: String = "products/thumbnail.png",
        additionalImageKeys: List<String> = listOf("products/a1.png"),
        detailImageKeys: List<String> = listOf("products/d1.png"),
        extra: String = "",
    ): String {
        val optional =
            listOfNotNull(
                description?.let { "\"description\": \"$it\"," },
                discountPrice?.let { "\"discountPrice\": $it," },
                discountStartAt?.let { "\"discountStartAt\": \"$it\"," },
                discountEndAt?.let { "\"discountEndAt\": \"$it\"," },
                extra.ifEmpty { null },
            ).joinToString("\n")
        return """
            {
              "categoryId": $categoryId,
              "productName": "$productName",
              "regularPrice": $regularPrice,
              $optional
              "thumbnailImageKey": "$thumbnailImageKey",
              "additionalImageKeys": ${additionalImageKeys.toJsonArray()},
              "detailImageKeys": ${detailImageKeys.toJsonArray()}
            }
            """.trimIndent()
    }

    private fun List<String>.toJsonArray(): String = joinToString(", ", prefix = "[", postfix = "]") { "\"$it\"" }

    /** 할인 세 필드를 함께 실어 보냄. 두 번 부르면서 한 필드만 달리해야 그 필드가 단독으로 고정됨. */
    private fun applyDiscount(
        discountPrice: Long,
        startAt: String,
        endAt: String,
        expectedVersionNo: Int,
    ) {
        mockMvc
            .perform(
                updateRequest(
                    token,
                    productId,
                    updateJson(discountPrice = discountPrice, discountStartAt = startAt, discountEndAt = endAt),
                ),
            ).andExpect(status().isOk)
        assertEquals(
            (1..expectedVersionNo).toList(),
            versionNos(),
            "이 요청까지 버전이 $expectedVersionNo 번째여야 한다: ${versionNos()}",
        )
    }

    companion object {
        private const val DISCOUNT_START = "2026-08-11T10:00:00"
        private const val DISCOUNT_END = "2026-08-20T10:00:00"
    }
}
