package com.aechak.api.config

import com.aechak.api.auth.response.LoginResponse
import com.aechak.api.order.cart.response.CartResponse
import com.aechak.api.product.response.ProductSummaryResponse
import com.aechak.api.review.response.ReviewItemResponse
import com.aechak.api.support.IntegrationTestBase
import com.aechak.api.user.term.response.TermResponse
import com.aechak.api.user.user.response.UserMeResponse
import com.aechak.domain.order.cart.enums.CartItemStatus
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.claim.enums.RefundStatus
import com.aechak.domain.order.group.enums.OrderGroupStatus
import com.aechak.domain.order.order.enums.OrderItemStatus
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.user.term.enums.TermType
import com.aechak.domain.user.user.enums.UserStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 실제 컨테이너의 ObjectMapper가 계약 필드명을 그대로 내보내는지 검증.
 * jackson-module-kotlin 부재 시 is-접두 프로퍼티가 "required"처럼 잘려 나가는 회귀를 막는다.
 * 여기서 고정한 와이어 이름이 생성 문서와 같은지는 OpenApiContractIntegrationTest가 본다.
 */
class JacksonWireFormatTest : IntegrationTestBase() {
    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Test
    fun `is-접두 Kotlin 프로퍼티는 필드명이 잘리지 않고 그대로 직렬화된다`() {
        val json = objectMapper.writeValueAsString(term())

        assertTrue(json.contains("\"isRequired\":true"), "계약 필드명 보존 실패: $json")
    }

    /** 문서가 접두를 떼어 적고 있던 자리들 — 실제 응답이 접두를 유지하는 쪽임을 고정한다. */
    @TestFactory
    fun `is-접두 불리언의 와이어 필드명`(): List<DynamicTest> =
        listOf(
            "isOrderable" to cartItem(),
            "isMine" to reviewItem(),
            "isNew" to loginUser(),
            "isLiked" to productSummary(),
            "isPhoneVerified" to userMe(),
        ).map { (field, payload) ->
            DynamicTest.dynamicTest("$field — ${payload.javaClass.simpleName}") {
                val json = objectMapper.writeValueAsString(payload)
                val stripped = field.removePrefix("is").replaceFirstChar(Char::lowercase)

                assertTrue(json.contains("\"$field\":"), "$field 가 그대로 나가지 않는다: $json")
                assertTrue(!json.contains("\"$stripped\":"), "접두가 떨어진 '$stripped' 로 나간다: $json")
            }
        }

    /**
     * 상태 필드를 String에서 enum으로 바꾼 전제 — enum은 name()과 같은 문자열로 직렬화된다.
     * @JsonValue·toString 재정의나 매퍼 설정이 들어오면 이 단언이 먼저 깨진다.
     */
    @TestFactory
    fun `상태 enum은 name과 같은 문자열로 직렬화된다`(): List<DynamicTest> =
        listOf(
            OrderStatus.entries,
            OrderItemStatus.entries,
            OrderGroupStatus.entries,
            CartItemStatus.entries,
            SaleStatus.entries,
            ClaimType.entries,
            ClaimStatus.entries,
            RefundStatus.entries,
            UserStatus.entries,
        ).flatten()
            .map { value ->
                DynamicTest.dynamicTest("${value.javaClass.simpleName}.${value.name}") {
                    assertEquals("\"${value.name}\"", objectMapper.writeValueAsString(value))
                }
            }

    private fun term() =
        TermResponse(
            termId = 1L,
            type = TermType.SERVICE,
            isRequired = true,
            version = "1.0",
            title = "서비스 이용약관",
            body = "본문",
            effectiveAt = LocalDateTime.of(2026, 7, 1, 0, 0),
        )

    private fun cartItem() =
        CartResponse.CartItemResponse(
            cartItemId = 1L,
            productId = "01JPRODUCT",
            optionCombinationId = 2L,
            productName = "강아지 사료",
            thumbnail = null,
            selectedOptions = "1kg",
            quantity = 1,
            originalPrice = 10_000L,
            price = 9_000L,
            discountRate = 10,
            remainingStock = 3,
            itemStatus = CartItemStatus.ACTIVE,
            isOrderable = true,
        )

    private fun reviewItem() =
        ReviewItemResponse(
            reviewId = 1L,
            rating = 5,
            content = "좋아요",
            optionName = "1kg",
            authorNickname = "애착러",
            authorProfileImageUrl = null,
            createdAt = LocalDateTime.of(2026, 7, 1, 0, 0),
            images = emptyList(),
            isMine = true,
        )

    private fun loginUser() = LoginResponse.User(status = UserStatus.ACTIVE, isNew = true)

    private fun productSummary() =
        ProductSummaryResponse(
            productId = "01JPRODUCT",
            name = "강아지 사료",
            sellerName = "애착상회",
            thumbnailImageKey = null,
            regularPrice = 10_000L,
            discountPrice = 9_000L,
            discountRate = 10,
            saleStatus = SaleStatus.ON_SALE,
            averageRating = BigDecimal("4.5"),
            reviewCount = 2,
            isViewable = true,
            isPurchasable = true,
            isLiked = true,
        )

    private fun userMe() =
        UserMeResponse(
            status = UserStatus.ACTIVE,
            nickname = "애착러",
            profileImageUrl = null,
            profileImageKey = null,
            bio = null,
            email = null,
            phoneNumber = null,
            isPhoneVerified = true,
        )
}
