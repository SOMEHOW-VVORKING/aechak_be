package com.aechak.seller.order

import com.aechak.api.support.IntegrationTestBase
import com.aechak.domain.order.shipment.CourierMaster
import com.aechak.domain.seller.seller.Seller
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * 택배사 마스터 조회 통합 테스트. 활성 필터·정렬·인증 게이트를 고정한다.
 * 시드는 마이그레이션이 넣지만 테스트 스키마는 ddl-auto로 만들고 매 테스트 비우므로 행을 직접 심는다.
 */
class CourierListIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    private lateinit var mockMvc: MockMvc
    private lateinit var token: String

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
        val sellerUserId = createActiveUser()
        token = mintAccessToken(sellerUserId)
        tx.execute { em.persist(Seller.open(sellerUserId, "애착상회", 0L)) }
    }

    @Test
    fun `활성 택배사를 이름 오름차순으로 반환한다`() {
        seedDefaultCouriers()

        list()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items.length()").value(5))
            .andExpect(jsonPath("$.data.items[0].courierCode").value("CJ"))
            .andExpect(jsonPath("$.data.items[0].courierName").value("CJ대한통운"))
            .andExpect(jsonPath("$.data.items[1].courierCode").value("LOGEN"))
            .andExpect(jsonPath("$.data.items[2].courierCode").value("LOTTE"))
            .andExpect(jsonPath("$.data.items[3].courierCode").value("POST"))
            .andExpect(jsonPath("$.data.items[4].courierCode").value("HANJIN"))
    }

    @Test
    fun `비활성 택배사는 제외한다`() {
        seedCourier("CJ", "CJ대한통운")
        seedCourier("LOTTE", "롯데택배", isActive = false)

        list()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].courierCode").value("CJ"))
    }

    // 필드 누락이 아니라 명시적 null이어야 한다 — FE가 "검증 패턴 없음"을 키 부재로 추론하지 않게
    @Test
    fun `송장번호 형식이 미정이면 null로 내린다`() {
        seedCourier("CJ", "CJ대한통운", trackingFormatPattern = null)
        seedCourier("POST", "우체국택배", trackingFormatPattern = "^\\d{13}$")

        list()
            .andExpect(jsonPath("$.data.items[0].trackingFormatPattern").value(nullValue()))
            .andExpect(jsonPath("$.data.items[1].trackingFormatPattern").value("^\\d{13}$"))
    }

    @Test
    fun `이름이 같으면 코드 오름차순으로 반환한다`() {
        seedCourier("ZZ-LOTTE", "롯데택배")
        seedCourier("AA-LOTTE", "롯데택배")

        list()
            .andExpect(jsonPath("$.data.items.length()").value(2))
            .andExpect(jsonPath("$.data.items[0].courierCode").value("AA-LOTTE"))
            .andExpect(jsonPath("$.data.items[1].courierCode").value("ZZ-LOTTE"))
    }

    @Test
    fun `택배사가 없으면 빈 목록이다`() {
        list()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items.length()").value(0))
    }

    @Test
    fun `토큰 없이 조회하면 20004를 반환한다`() {
        seedDefaultCouriers()

        mockMvc
            .perform(get(LIST_URL))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value(20004))
    }

    // 셀러 자격을 보지 않는다 — 셀러에 종속되지 않는 공용 마스터 데이터다
    @Test
    fun `셀러가 아닌 사용자도 조회할 수 있다`() {
        seedDefaultCouriers()

        list(asToken = mintAccessToken(createActiveUser()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items.length()").value(5))
    }

    private fun list(asToken: String = token): ResultActions =
        mockMvc.perform(get(LIST_URL).header(HttpHeaders.AUTHORIZATION, "Bearer $asToken"))

    private fun seedDefaultCouriers() {
        seedCourier("LOTTE", "롯데택배")
        seedCourier("CJ", "CJ대한통운")
        seedCourier("POST", "우체국택배")
        seedCourier("HANJIN", "한진택배")
        seedCourier("LOGEN", "로젠택배")
    }

    private fun seedCourier(
        courierCode: String,
        courierName: String,
        isActive: Boolean = true,
        trackingFormatPattern: String? = null,
    ) {
        tx.execute {
            em.persist(
                CourierMaster.register(
                    courierCode = courierCode,
                    courierName = courierName,
                    isActive = isActive,
                    trackingFormatPattern = trackingFormatPattern,
                ),
            )
        }
    }

    companion object {
        private const val LIST_URL = "/api/v1/sellers/couriers"
    }
}
