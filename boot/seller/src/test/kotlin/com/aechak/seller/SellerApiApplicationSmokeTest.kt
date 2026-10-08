package com.aechak.seller

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.seller.usecase.AdminSellerReviewUseCase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import kotlin.test.assertTrue

/** 선별 스캔 목록이 요구하는 빈이 전부 조립되는지 보는 스모크. 스캔 패키지를 늘릴 때 빠진 어댑터가 여기서 드러남. */
class SellerApiApplicationSmokeTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: ApplicationContext

    @Test
    fun contextLoads() {
    }

    /**
     * 어드민 전용 구현체는 같은 BC 패키지(application.seller)에 살지만 셀러센터는 쓰지 않는다.
     * 컨텍스트 로딩만으로는 "불필요한 빈이 조립되는 것"을 잡지 못하므로 부재를 직접 확인한다 —
     * excludeFilters가 지워지거나 Admin 접두사 규약이 깨지면 여기서 먼저 드러난다.
     */
    @Test
    fun `어드민 전용 빈은 셀러센터 컨텍스트에 조립되지 않는다`() {
        val names = context.getBeanNamesForType(AdminSellerReviewUseCase::class.java)

        assertTrue(
            names.isEmpty(),
            "어드민 전용 빈이 셀러센터에 조립됐다 (${names.joinToString()}) — " +
                "excludeFilters 또는 Admin 접두사 규약을 확인할 것",
        )
    }
}
