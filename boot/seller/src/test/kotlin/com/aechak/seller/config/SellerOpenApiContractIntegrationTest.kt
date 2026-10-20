package com.aechak.seller.config

import com.aechak.api.support.HandlerStatuses
import com.aechak.api.support.IntegrationTestBase
import com.aechak.api.support.OpenApiDocContract
import com.aechak.api.support.OpenApiSpec
import com.aechak.application.order.seller.port.SellerOrderStatusFilter
import com.aechak.application.settlement.port.SellerSettlementStatus
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.claim.enums.RefundStatus
import com.aechak.domain.order.order.enums.OrderItemStatus
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.product.product.enums.InspectionStatus
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.seller.settlement.request.SellerSettlementSearchRequest
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

/** 셀러 보트의 생성 문서와 실제 응답이 어긋나지 않는지 본다. */
class SellerOpenApiContractIntegrationTest : IntegrationTestBase() {
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
        val collisions = OpenApiDocContract.schemaNameCollisions(BASE_PACKAGE, spec.schemas.keys, DTO_PACKAGES)

        assertTrue(collisions.isEmpty(), "단순 이름이 겹쳐 한 벌만 남는 스키마가 있다: $collisions")
    }

    @Test
    fun `상품 등록의 옵션 항목과 임시저장의 옵션 항목이 둘 다 문서에 있다`() {
        assertEquals(setOf("string"), spec.types("ProductRegisterOptionGroupItem", "name"), "등록의 그룹명은 필수다")
        assertEquals(setOf("string", "null"), spec.types("OptionGroupItem", "name"), "임시저장의 그룹명은 널 허용이다")

        assertEquals(
            setOf("integer"),
            spec.types("ProductRegisterOptionCombinationItem", "stockQuantity"),
            "등록의 재고는 필수다",
        )
        assertEquals(
            setOf("integer", "null"),
            spec.types("OptionCombinationItem", "stockQuantity"),
            "임시저장의 재고는 널 허용이다",
        )
    }

    @Test
    fun `is-접두 불리언은 문서 필드명도 접두를 유지한다`() {
        val mismatches = OpenApiDocContract.isPrefixMismatches(BASE_PACKAGE, spec.schemas, DTO_PACKAGES)

        assertTrue(mismatches.isEmpty(), "문서가 실제 직렬화 이름과 다른 자리가 있다(실제는 is 접두를 유지한다): $mismatches")
    }

    @Test
    fun `요청 스키마에 내부 검증 프로퍼티가 노출되지 않는다`() {
        val exposed = OpenApiDocContract.documentedSelfValidationProperties(BASE_PACKAGE, spec.schemas, DTO_PACKAGES)

        assertTrue(exposed.isEmpty(), "요청이 스스로를 검사하는 프로퍼티가 요청 필드로 올라와 있다: $exposed")
    }

    @Test
    fun `상태 필드에 허용값이 붙어 있다`() {
        val missing = OpenApiDocContract.statusPropertiesWithoutAllowedValues(spec.schemas, FREE_FORM_STATUS_PROPERTIES)

        assertTrue(missing.isEmpty(), "허용값 없이 문자열로만 나가는 상태 필드가 있다: $missing")
    }

    @Test
    fun `셀러 주문 계열 상태의 허용값이 서버 enum과 같다`() {
        assertEquals(names<OrderStatus>(), spec.allowedValues("SellerOrderSummaryResponse", "status"))
        assertEquals(names<OrderStatus>(), spec.allowedValues("SellerOrderDetailResponse", "status"))
        assertEquals(names<OrderStatus>(), spec.allowedValues("ShipmentRegisterResponse", "orderStatus"))
        assertEquals(names<OrderItemStatus>(), spec.allowedValues("SellerOrderLineResponse", "itemStatus"))
        assertEquals(names<ClaimType>(), spec.allowedValues("SellerOrderClaimResponse", "claimType"))
        assertEquals(names<ClaimStatus>(), spec.allowedValues("SellerOrderClaimResponse", "status"))
    }

    @Test
    fun `셀러 상품 목록 행의 상태 허용값이 상세와 같다`() {
        assertEquals(names<SaleStatus>(), spec.allowedValues("SellerProductSummaryResponse", "saleStatus"))
        assertEquals(names<InspectionStatus>(), spec.allowedValues("SellerProductSummaryResponse", "inspectionStatus"))
        assertEquals(
            spec.allowedValues("SellerProductDetailResponse", "saleStatus"),
            spec.allowedValues("SellerProductSummaryResponse", "saleStatus"),
        )
    }

    @Test
    fun `목록 조회 필터의 허용값이 서버가 받는 값과 같다`() {
        assertEquals(names<SaleStatus>(), spec.allowedValues("SellerProductSearchRequest", "saleStatus"))
        assertEquals(names<InspectionStatus>(), spec.allowedValues("SellerProductSearchRequest", "inspectionStatus"))
        assertEquals(names<ClaimType>(), spec.allowedValues("SellerClaimSearchRequest", "claimType"))
        assertEquals(names<ClaimStatus>(), spec.allowedValues("SellerClaimSearchRequest", "status"))
        assertEquals(names<RefundStatus>(), spec.allowedValues("SellerClaimSearchRequest", "refundStatus"))
    }

    @Test
    fun `정산 상태의 허용값이 서버가 쓰는 화면 어휘와 같다`() {
        val screenVocabulary = SellerSettlementStatus.entries.map { it.name.lowercase() }.toSet()

        assertEquals(screenVocabulary, spec.allowedValues("SellerSettlementListItemResponse", "status"))
        assertEquals(screenVocabulary, spec.allowedValues("SellerSettlementDetailResponse", "status"))
        assertEquals(screenVocabulary, spec.allowedValues("SellerSettlementSearchRequest", "status"))
    }

    /** 정산 필터는 enum 이름이 아니라 손으로 쓴 분기로 받으므로, 문서 값이 그 분기를 통과하는지까지 본다. */
    @Test
    fun `문서가 적은 정산 필터 값은 서버 파서가 전부 받는다`() {
        val parsed =
            spec.allowedValues("SellerSettlementSearchRequest", "status").associateWith { value ->
                SellerSettlementSearchRequest(status = value).toQuery(sellerId = 1L).status
            }

        assertEquals(
            SellerSettlementStatus.entries.toSet(),
            parsed.values.toSet(),
            "문서 값과 서버 파서가 해석하는 상태가 일대일로 맞지 않는다: $parsed",
        )
    }

    @Test
    fun `셀러 주문 상태 필터의 허용값이 서버가 받는 세그먼트 코드와 같다`() {
        assertEquals(
            SellerOrderStatusFilter.entries.map { it.code }.toSet(),
            spec.allowedValues("SellerOrderSearchRequest", "status"),
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
        assertEquals(setOf("204"), spec.operationResponseCodes["POST /api/v1/sellers/me/orders/{orderPublicId}/accept"])
        assertEquals(setOf("201"), spec.operationResponseCodes["POST /api/v1/sellers/me/orders/{orderPublicId}/shipment"])
        assertEquals(setOf("204"), spec.operationResponseCodes["PUT /api/v1/sellers/me/store"])
    }

    private inline fun <reified E : Enum<E>> names(): Set<String> = enumValues<E>().map { it.name }.toSet()

    companion object {
        private const val BASE_PACKAGE = "com.aechak.seller"

        /** 초안 페이로드는 request·response 패키지 밖에 있어 따로 지목한다. */
        private val DTO_PACKAGES = setOf("com.aechak.seller.product.support")

        /** 택배사가 주는 자유 문구라서 고정할 허용값이 없다. */
        private val FREE_FORM_STATUS_PROPERTIES = setOf("lastTrackingStatus")
        private val DUMP_PATH: Path = Path.of("build", "openapi", "seller-api-docs.json")
    }
}
