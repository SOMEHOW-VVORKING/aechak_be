package com.aechak.seller

import com.aechak.api.support.IntegrationTestBase
import org.junit.jupiter.api.Test

/** 선별 스캔 목록이 요구하는 빈이 전부 조립되는지 보는 스모크. 스캔 패키지를 늘릴 때 빠진 어댑터가 여기서 드러남. */
class SellerApiApplicationSmokeTest : IntegrationTestBase() {
    @Test
    fun contextLoads() {
    }
}
