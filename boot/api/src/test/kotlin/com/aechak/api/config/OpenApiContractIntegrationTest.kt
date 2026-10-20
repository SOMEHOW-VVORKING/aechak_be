package com.aechak.api.config

import com.aechak.api.support.HandlerStatuses
import com.aechak.api.support.IntegrationTestBase
import com.aechak.api.support.OpenApiDocContract
import com.aechak.api.support.OpenApiSpec
import com.aechak.application.order.port.OrderStatusFilter
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.claim.enums.RefundStatus
import com.aechak.domain.order.group.enums.OrderGroupStatus
import com.aechak.domain.order.order.enums.OrderItemStatus
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.product.product.enums.SaleStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.ObjectMapper
import java.nio.file.Path

/**
 * 생성 문서와 실제 응답이 어긋나지 않는지 본다. FE가 스키마를 수기로 맞춰 두는 것 말고는
 * 두 쪽이 벌어졌는지 알 길이 없던 상태를 벗어나려고 둔다.
 */
class OpenApiContractIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private lateinit var spec: OpenApiSpec

    @BeforeEach
    fun fetchSpec() {
        val raw =
            MockMvcBuilders
                .webAppContextSetup(context)
                .build()
                .perform(get(OpenApiSpec.API_DOCS_PATH))
                .andReturn()
                .response
                .contentAsString
        spec = OpenApiSpec.of(raw, objectMapper, DUMP_PATH)
    }

    @Test
    fun `문서 스키마 이름을 두 클래스가 나눠 쓰지 않는다`() {
        val collisions = OpenApiDocContract.schemaNameCollisions(BASE_PACKAGE, spec.schemas.keys)

        assertTrue(collisions.isEmpty(), "단순 이름이 겹쳐 한 벌만 남는 스키마가 있다: $collisions")
    }

    @Test
    fun `카테고리 트리 응답과 상품 상세의 경로 표시용 응답이 둘 다 문서에 있다`() {
        assertEquals(
            setOf("categoryId", "name", "iconUrl", "sortOrder", "children"),
            spec.propertyNames("CategoryResponse"),
            "트리 조회 응답이 상품 상세의 경로 표시용 스키마에 덮였다",
        )
        assertEquals(
            setOf("categoryId", "name", "depth"),
            spec.propertyNames("ProductCategoryPathResponse"),
            "상품 상세의 경로 표시용 스키마가 사라졌다",
        )
    }

    @Test
    fun `is-접두 불리언은 문서 필드명도 접두를 유지한다`() {
        val mismatches = OpenApiDocContract.isPrefixMismatches(BASE_PACKAGE, spec.schemas)

        assertTrue(mismatches.isEmpty(), "문서가 실제 직렬화 이름과 다른 자리가 있다(실제는 is 접두를 유지한다): $mismatches")
    }

    @Test
    fun `요청 스키마에 내부 검증 프로퍼티가 노출되지 않는다`() {
        assertEquals(setOf("items"), spec.propertyNames("SubmitConsentsRequest"))
        assertTrue(
            spec.propertyNames("ProductKeywordSearchRequest").none { it.contains("priceRangeValid", ignoreCase = true) },
            "검색 요청에 내부 검증 프로퍼티가 남아 있다: ${spec.propertyNames("ProductKeywordSearchRequest")}",
        )
    }

    @Test
    fun `상태 필드에 허용값이 붙어 있다`() {
        val missing = OpenApiDocContract.statusPropertiesWithoutAllowedValues(spec.schemas, FREE_FORM_STATUS_PROPERTIES)

        assertTrue(missing.isEmpty(), "허용값 없이 문자열로만 나가는 상태 필드가 있다: $missing")
    }

    @Test
    fun `주문 계열 상태의 허용값이 서버 enum과 같다`() {
        assertEquals(names<OrderStatus>(), spec.allowedValues("OrderDetailResponse", "status"))
        assertEquals(names<OrderGroupStatus>(), spec.allowedValues("OrderGroupDetailResponse", "status"))
        assertEquals(names<OrderStatus>(), spec.allowedValues("SellerOrderResponse", "status"))
        assertEquals(names<OrderStatus>(), spec.allowedValues("SellerOrderItemResponse", "status"))
        assertEquals(names<OrderItemStatus>(), spec.allowedValues("OrderLineItemResponse", "itemStatus"))
        assertEquals(names<SaleStatus>(), spec.allowedValues("ProductDetailResponse", "saleStatus"))
        assertEquals(names<SaleStatus>(), spec.allowedValues("ProductSummaryResponse", "saleStatus"))
    }

    @Test
    fun `주문 상세의 클레임 요약 허용값이 클레임 전용 스키마와 같다`() {
        assertEquals(names<ClaimType>(), spec.allowedValues("OrderActiveClaimResponse", "claimType"))
        assertEquals(names<ClaimStatus>(), spec.allowedValues("OrderActiveClaimResponse", "status"))
        assertEquals(names<RefundStatus>(), spec.allowedValues("OrderActiveClaimResponse", "refundStatus"))
        assertEquals(
            spec.allowedValues("ClaimItem", "claimType"),
            spec.allowedValues("OrderActiveClaimResponse", "claimType"),
        )
        assertEquals(
            spec.allowedValues("ClaimItem", "status"),
            spec.allowedValues("OrderActiveClaimResponse", "status"),
        )
    }

    @Test
    fun `주문 목록 상태 필터의 허용값이 서버가 받는 세그먼트 코드와 같다`() {
        assertEquals(
            OrderStatusFilter.entries.map { it.code }.toSet(),
            spec.allowedValues("OrderListRequest", "status"),
            "문서의 허용값과 서버가 실제로 받는 코드가 다르다",
        )
    }

    @Test
    fun `문서가 적은 성공 코드가 핸들러가 선언한 코드와 같다`() {
        val handlers = HandlerStatuses.of(context, BASE_PACKAGE)

        val mismatches =
            OpenApiDocContract.statusCodeMismatches(
                spec.operationResponseCodes,
                handlers.declaredStatuses,
                handlers.bodylessOperations,
            )

        assertTrue(mismatches.isEmpty(), "문서의 성공 코드가 실제와 다른 자리가 있다: $mismatches")
    }

    @Test
    fun `본문 없이 돌려주는 경로의 성공 코드가 문서에 그대로 적힌다`() {
        assertEquals(setOf("204"), spec.operationResponseCodes["POST /api/v1/orders/{orderPublicId}/confirm"])
        assertEquals(setOf("204"), spec.operationResponseCodes["POST /api/v1/users/me/consents"])
        assertEquals(setOf("204"), spec.operationResponseCodes["DELETE /api/v1/users/me"])
        assertEquals(setOf("201"), spec.operationResponseCodes["POST /api/v1/products/{productId}/like"])
        assertEquals(setOf("204"), spec.operationResponseCodes["DELETE /api/v1/products/{productId}/like"])
        assertEquals(setOf("302"), spec.operationResponseCodes["GET /api/v1/auth/callback/{provider}"])
    }

    private inline fun <reified E : Enum<E>> names(): Set<String> = enumValues<E>().map { it.name }.toSet()

    companion object {
        private const val BASE_PACKAGE = "com.aechak.api"

        /** 택배사가 주는 자유 문구라서 고정할 허용값이 없다. */
        private val FREE_FORM_STATUS_PROPERTIES = setOf("lastTrackingStatus")
        private val DUMP_PATH: Path = Path.of("build", "openapi", "api-docs.json")
    }
}
