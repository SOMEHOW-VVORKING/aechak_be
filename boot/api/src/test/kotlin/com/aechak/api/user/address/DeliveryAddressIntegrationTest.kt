package com.aechak.api.user.address

import com.aechak.api.support.IntegrationTestBase
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.user.address.enums.DeliveryAddressStatus
import com.aechak.domain.user.error.UserErrorCode
import com.aechak.domain.user.user.User
import com.aechak.domain.user.user.enums.UserStatus
import com.aechak.websecurity.config.JwtConfig
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.Instant

/**
 * 배송지 주소록 CRUD 통합 테스트 (실 MySQL). 실제 보안 필터체인을 통과시키기 위해
 * ACTIVE 유저를 심고 자체 RS256 JWT를 발급해 Authorization 헤더로 인증한다.
 * Boot 4에는 @AutoConfigureMockMvc 슬라이스가 없어 WebApplicationContext + 실 보안 필터체인으로 MockMvc를 조립한다.
 * 한글 응답은 MockMvc 기본 charset 이슈를 피해 UTF-8로 직접 파싱한다.
 */
class DeliveryAddressIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    @Autowired
    private lateinit var jwtEncoder: JwtEncoder

    private lateinit var mockMvc: MockMvc
    private var ownerId = 0L
    private var otherId = 0L
    private lateinit var ownerToken: String
    private lateinit var otherToken: String

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
        ownerId = createActiveUser()
        otherId = createActiveUser()
        ownerToken = mintAccessToken(ownerId)
        otherToken = mintAccessToken(otherId)
    }

    @Test
    fun `배송지를 추가하면 201을 반환하고 본인 목록에만 노출된다`() {
        mockMvc
            .perform(postDeliveryAddress(ownerToken, addressJson(receiverName = "홍길동")))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.addressId").exists())
            .andExpect(jsonPath("$.data.isDefault").value(false))
            .andExpect(jsonPath("$.data.totalCount").value(1))

        addDeliveryAddress(otherToken, addressJson(receiverName = "타인"))

        val body = listBody(ownerToken)
        assertEquals(1, JsonPath.read<Int>(body, "$.data.totalCount"))
        assertEquals(1, (JsonPath.read(body, "$.data.addresses") as List<*>).size, "타인 배송지는 보이지 않아야 한다")
        assertEquals("홍길동", JsonPath.read<String>(body, "$.data.addresses[0].receiverName"))
        assertEquals("010-1234-5678", JsonPath.read<String>(body, "$.data.addresses[0].contactNumber"))
    }

    @Test
    fun `활성 배송지가 10개면 11번째 추가는 422와 DELIVERY_ADDRESS_LIMIT_EXCEEDED를 반환한다`() {
        repeat(10) { addDeliveryAddress(ownerToken, addressJson(label = "addr$it")) }

        mockMvc
            .perform(postDeliveryAddress(ownerToken, addressJson()))
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.errorCode").value(UserErrorCode.DELIVERY_ADDRESS_LIMIT_EXCEEDED.code))
    }

    @Test
    fun `연락처 형식이 틀리면 400을 반환한다`() {
        // 파이프 리터럴 제거 회귀(01|-...), 잘못된 prefix, too-short 포함
        listOf("123-456", "01|-1234-5678", "021-1234-5678", "010-12-5678").forEach { bad ->
            mockMvc
                .perform(postDeliveryAddress(ownerToken, addressJson(contactNumber = bad)))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.INVALID_REQUEST.code))
        }
    }

    @Test
    fun `필드 길이 초과는 400을 반환한다`() {
        mockMvc
            .perform(postDeliveryAddress(ownerToken, addressJson(receiverName = "가".repeat(256))))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.INVALID_REQUEST.code))
            // {max} 인터폴레이션이 실제 숫자로 렌더되는지까지 고정
            .andExpect(jsonPath("$.message").value("receiverName: 수령인 이름은 255자를 넘을 수 없습니다."))
    }

    @Test
    fun `상세 주소가 비어 있으면 400과 INVALID_REQUEST를 반환한다`() {
        mockMvc
            .perform(postDeliveryAddress(ownerToken, addressJson(detailedAddress = "")))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.INVALID_REQUEST.code))
    }

    @Test
    fun `기본 배송지로 추가하면 기존 기본은 해제된다`() {
        val first = addDeliveryAddress(ownerToken, addressJson(label = "first", isDefault = true))
        val second = addDeliveryAddress(ownerToken, addressJson(label = "second", isDefault = true))

        val defaults = activeDefaultIds(ownerId)
        assertEquals(listOf(second), defaults, "가장 최근에 기본으로 추가한 것만 기본이어야 한다")
        assertFalse(first in defaults)
    }

    @Test
    fun `본인 배송지 수정은 200과 변경된 값을 반영한다`() {
        val addr = addDeliveryAddress(ownerToken, addressJson(receiverName = "홍길동"))

        mockMvc
            .perform(patchDeliveryAddress(ownerToken, addr, addressJson(receiverName = "김철수", isDefault = true)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.isDefault").value(true))

        val body = listBody(ownerToken)
        assertEquals("김철수", JsonPath.read<String>(body, "$.data.addresses[0].receiverName"))
    }

    @Test
    fun `수정에서 isDefault를 생략하면 기존 기본 여부는 유지된다`() {
        val id = addDeliveryAddress(ownerToken, addressJson(isDefault = true))

        // 전체 객체 PATCH지만 isDefault=false → 기본을 건드리지 않아 그대로 유지된다.
        mockMvc
            .perform(patchDeliveryAddress(ownerToken, id, addressJson(receiverName = "수정됨", isDefault = false)))
            .andExpect(status().isOk)

        assertEquals(listOf(id), activeDefaultIds(ownerId), "수정은 기본을 해제하지 않는다")
    }

    @Test
    fun `수정에서 isDefault=true면 기본 설정되고 기존 기본은 해제된다`() {
        val a = addDeliveryAddress(ownerToken, addressJson(label = "a", isDefault = true))
        val b = addDeliveryAddress(ownerToken, addressJson(label = "b"))

        mockMvc
            .perform(patchDeliveryAddress(ownerToken, b, addressJson(label = "b", isDefault = true)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.isDefault").value(true))

        assertEquals(listOf(b), activeDefaultIds(ownerId), "b가 기본이 되고 a는 해제된다")
    }

    @Test
    fun `타인 배송지 수정은 403과 DELIVERY_ADDRESS_ACCESS_DENIED를 반환한다`() {
        val addr = addDeliveryAddress(ownerToken, addressJson())

        mockMvc
            .perform(patchDeliveryAddress(otherToken, addr, addressJson()))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(UserErrorCode.DELIVERY_ADDRESS_ACCESS_DENIED.code))
    }

    @Test
    fun `없는 배송지 수정은 404와 DELIVERY_ADDRESS_NOT_FOUND를 반환한다`() {
        mockMvc
            .perform(patchDeliveryAddress(ownerToken, 999_999L, addressJson()))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(UserErrorCode.DELIVERY_ADDRESS_NOT_FOUND.code))
    }

    @Test
    fun `기본 배송지 삭제시 updated_at 최신 활성건이 자동 승격된다`() {
        val default = addDeliveryAddress(ownerToken, addressJson(label = "default", isDefault = true))
        val a = addDeliveryAddress(ownerToken, addressJson(label = "a"))
        val b = addDeliveryAddress(ownerToken, addressJson(label = "b")) // b가 최신 id/createdAt
        // a만 갱신해 a.updatedAt > b.updatedAt로 만든다 → 승격 규칙을 id/createdAt와 격리한다.
        mockMvc.perform(patchDeliveryAddress(ownerToken, a, addressJson(label = "a-updated"))).andExpect(status().isOk)

        val body =
            mockMvc
                .perform(delete("/api/v1/delivery-addresses/$default").bearer(ownerToken))
                .andExpect(status().isOk)
                .andReturn()
                .response
                .contentAsString

        assertEquals(a, readId(body, "$.data.promotedDefaultAddressId"), "id/createdAt 최신은 b지만 updated_at 최신 a가 승격돼야 한다")
        assertEquals(listOf(a), activeDefaultIds(ownerId))
    }

    @Test
    fun `마지막 활성 배송지를 삭제하면 승격 없이 null을 반환한다`() {
        val only = addDeliveryAddress(ownerToken, addressJson(isDefault = true))

        val body =
            mockMvc
                .perform(delete("/api/v1/delivery-addresses/$only").bearer(ownerToken))
                .andExpect(status().isOk)
                .andReturn()
                .response
                .contentAsString

        assertNull(JsonPath.read<Any?>(body, "$.data.promotedDefaultAddressId"))
        assertEquals(emptyList<Long>(), activeDefaultIds(ownerId))
    }

    @Test
    fun `기본 지정하면 지정건만 기본이고 나머지는 해제된다`() {
        addDeliveryAddress(ownerToken, addressJson(label = "a", isDefault = true))
        val b = addDeliveryAddress(ownerToken, addressJson(label = "b"))
        addDeliveryAddress(ownerToken, addressJson(label = "c"))

        mockMvc
            .perform(patch("/api/v1/delivery-addresses/$b/default").bearer(ownerToken))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.isDefault").value(true))

        assertEquals(listOf(b), activeDefaultIds(ownerId))
    }

    @Test
    fun `deliveryMemo는 raw로 저장되고 재수정 후에도 원문이 보존된다`() {
        val memo = "부재 시 & 문 앞 <경비실>"
        val id = addDeliveryAddress(ownerToken, addressJson(deliveryMemo = memo))
        assertEquals(memo, JsonPath.read<String>(listBody(ownerToken), "$.data.addresses[0].deliveryMemo"), "저장단 escape 없이 raw 보존")

        // 전체 객체 PATCH 라운드트립 — 저장단 escape가 없어 &가 &amp;로 재이스케이프되지 않는다(멱등).
        mockMvc.perform(patchDeliveryAddress(ownerToken, id, addressJson(deliveryMemo = memo))).andExpect(status().isOk)
        assertEquals(memo, JsonPath.read<String>(listBody(ownerToken), "$.data.addresses[0].deliveryMemo"), "재수정 후에도 원문 보존")
    }

    @Test
    fun `목록 응답의 isDefault 키가 존재하고 정확히 한 건만 기본이다`() {
        addDeliveryAddress(ownerToken, addressJson(label = "a", isDefault = true))
        addDeliveryAddress(ownerToken, addressJson(label = "b"))

        // is-접두 직렬화 회귀 방어: 키가 'default'로 새면 이 경로가 비어 size 0으로 실패한다.
        val flags = JsonPath.read<List<Boolean>>(listBody(ownerToken), "$.data.addresses[*].isDefault")
        assertEquals(2, flags.size, "모든 항목에 isDefault 키가 있어야 한다")
        assertEquals(1, flags.count { it }, "정확히 한 건만 기본이어야 한다")
    }

    @Test
    fun `목록은 updated_at이 가장 오래됐어도 기본 배송지를 최상단에 노출한다`() {
        // 첫 등록이 기본 → 이후 다른 배송지를 추가/수정하면 updatedAt이 더 최신이라
        // 정렬이 updatedAt뿐이면 기본이 맨 아래로 밀린다. isDefault 선순위로 고정되는지 본다.
        addDeliveryAddress(ownerToken, addressJson(label = "old-default", isDefault = true))
        val b = addDeliveryAddress(ownerToken, addressJson(label = "b"))
        addDeliveryAddress(ownerToken, addressJson(label = "c"))
        mockMvc.perform(patchDeliveryAddress(ownerToken, b, addressJson(label = "b-updated"))).andExpect(status().isOk)

        val labels = JsonPath.read<List<String>>(listBody(ownerToken), "$.data.addresses[*].label")
        assertEquals("old-default", labels.first(), "updatedAt이 가장 오래된 기본이라도 맨 위여야 한다")
    }

    @Test
    fun `미인증 요청은 401을 반환한다`() {
        mockMvc.perform(get("/api/v1/delivery-addresses")).andExpect(status().isUnauthorized)
        mockMvc
            .perform(get("/api/v1/delivery-addresses").header(HttpHeaders.AUTHORIZATION, "Bearer garbage"))
            .andExpect(status().isUnauthorized)
        mockMvc
            .perform(post("/api/v1/delivery-addresses").contentType(MediaType.APPLICATION_JSON).content(addressJson()))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `삭제·기본지정도 타인이면 403, 없는 id면 404를 반환한다`() {
        val addr = addDeliveryAddress(ownerToken, addressJson())

        mockMvc
            .perform(delete("/api/v1/delivery-addresses/$addr").bearer(otherToken))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(UserErrorCode.DELIVERY_ADDRESS_ACCESS_DENIED.code))
        mockMvc
            .perform(delete("/api/v1/delivery-addresses/999999").bearer(ownerToken))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(UserErrorCode.DELIVERY_ADDRESS_NOT_FOUND.code))
        mockMvc
            .perform(patch("/api/v1/delivery-addresses/999999/default").bearer(ownerToken))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(UserErrorCode.DELIVERY_ADDRESS_NOT_FOUND.code))
    }

    // --- helpers ---

    private fun MockHttpServletRequestBuilder.bearer(token: String): MockHttpServletRequestBuilder =
        this.header(HttpHeaders.AUTHORIZATION, "Bearer $token")

    private fun postDeliveryAddress(
        token: String,
        body: String,
    ): MockHttpServletRequestBuilder =
        post("/api/v1/delivery-addresses")
            .bearer(token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)

    private fun patchDeliveryAddress(
        token: String,
        addressId: Long,
        body: String,
    ): MockHttpServletRequestBuilder =
        patch("/api/v1/delivery-addresses/$addressId")
            .bearer(token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)

    /** POST 후 생성된 addressId를 돌려준다. */
    private fun addDeliveryAddress(
        token: String,
        body: String,
    ): Long {
        val json =
            mockMvc
                .perform(postDeliveryAddress(token, body))
                .andExpect(status().isCreated)
                .andReturn()
                .response
                .contentAsString
        return readId(json, "$.data.addressId")
    }

    private fun listBody(token: String): String =
        mockMvc
            .perform(get("/api/v1/delivery-addresses").bearer(token))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .getContentAsString(Charsets.UTF_8)

    private fun readId(
        json: String,
        path: String,
    ): Long = (JsonPath.read(json, path) as Number).toLong()

    private fun activeDefaultIds(userId: Long): List<Long> =
        tx.execute {
            em
                .createQuery(
                    "select a.id from DeliveryAddress a " +
                        "where a.user.id = :uid and a.isDefault = true and a.status = :st order by a.id",
                    java.lang.Long::class.java,
                ).setParameter("uid", userId)
                .setParameter("st", DeliveryAddressStatus.ACTIVE)
                .resultList
                .map { it.toLong() }
        }!!

    private fun createActiveUser(): Long =
        tx.execute {
            val user = User.preRegister()
            em.persist(user)
            em.flush()
            em
                .createQuery("update User u set u.status = :st where u.id = :id")
                .setParameter("st", UserStatus.ACTIVE)
                .setParameter("id", user.id)
                .executeUpdate()
            user.id
        }!!

    private fun mintAccessToken(userId: Long): String {
        val now = Instant.now()
        val claims =
            JwtClaimsSet
                .builder()
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim(JwtConfig.ROLE_CLAIM, "GENERAL")
                .build()
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).tokenValue
    }

    private fun addressJson(
        receiverName: String = "홍길동",
        contactNumber: String = "010-1234-5678",
        zipCode: String = "06236",
        baseAddress: String = "서울시 강남구 테헤란로 152",
        detailedAddress: String? = "101동 1001호",
        deliveryMemo: String? = "부재 시 문 앞",
        label: String? = "집",
        isDefault: Boolean = false,
    ): String =
        """
        {
          "receiverName": "$receiverName",
          "contactNumber": "$contactNumber",
          "zipCode": "$zipCode",
          "baseAddress": "$baseAddress",
          "detailedAddress": ${jsonStr(detailedAddress)},
          "deliveryMemo": ${jsonStr(deliveryMemo)},
          "label": ${jsonStr(label)},
          "isDefault": $isDefault
        }
        """.trimIndent()

    private fun jsonStr(value: String?): String = if (value == null) "null" else "\"$value\""
}
