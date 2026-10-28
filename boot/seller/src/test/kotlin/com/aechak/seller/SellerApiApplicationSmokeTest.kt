package com.aechak.seller

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.messaging.MessagePublisher
import com.aechak.application.order.claim.usecase.ClaimAutoApproveUseCase
import com.aechak.application.order.claim.usecase.ClaimRefundUseCase
import com.aechak.application.seller.usecase.AdminSellerReviewUseCase
import com.aechak.infra.kafka.consumer.TraceIdRecordInterceptor
import com.aechak.infra.kafka.outbox.OutboxSweepTrigger
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

    /**
     * 환불 집행 구현체는 클레임 패키지에 살지만 결제 취소와 PG 어댑터를 요구한다.
     * 조립되면 셀러센터 부팅이 깨지므로 부재가 계약이다 — 집행은 배치가 맡는다.
     */
    @Test
    fun `환불 집행 빈은 셀러센터 컨텍스트에 조립되지 않는다`() {
        val names = context.getBeanNamesForType(ClaimRefundUseCase::class.java)

        assertTrue(
            names.isEmpty(),
            "환불 집행 빈이 셀러센터에 조립됐다 (${names.joinToString()}) — ClaimRefund 접두사 제외 필터를 확인할 것",
        )
    }

    /** 자동승인 구현체는 셀러 자격을 대조하지 않고 승인한다 — 셀러센터에 두면 자격 검증을 우회하는 경로가 생긴다. */
    @Test
    fun `자동승인 빈은 셀러센터 컨텍스트에 조립되지 않는다`() {
        val names = context.getBeanNamesForType(ClaimAutoApproveUseCase::class.java)

        assertTrue(
            names.isEmpty(),
            "자동승인 빈이 셀러센터에 조립됐다 (${names.joinToString()}) — ClaimAutoApprove 접두사 제외 필터를 확인할 것",
        )
    }

    /**
     * 검수 요청을 발행하려면 이 포트가 조립돼 있어야 한다.
     * 스캔 목록에서 발행 패키지가 빠지면 상품 등록이 부팅은 되고 발행만 조용히 사라진다.
     */
    @Test
    fun `메시지 발행 포트가 조립된다`() {
        val names = context.getBeanNamesForType(MessagePublisher::class.java)

        assertTrue(names.isNotEmpty(), "발행 포트가 없으면 검수 요청이 나가지 않는다")
    }

    /**
     * 셀러센터는 메시지를 받지 않는다. 컨슈머 부품이 따라 들어오면 쓰지 않는 빈을 조립하는 것이고,
     * 더 나쁘게는 판정 수신이 두 모듈에서 중복으로 돌 수 있다.
     */
    @Test
    fun `컨슈머 부품은 셀러센터에 조립되지 않는다`() {
        val interceptors = context.getBeanNamesForType(TraceIdRecordInterceptor::class.java)
        val sweepers = context.getBeanNamesForType(OutboxSweepTrigger::class.java)

        assertTrue(interceptors.isEmpty(), "컨슈머 인터셉터가 조립됐다 (${interceptors.joinToString()})")
        assertTrue(
            sweepers.isEmpty(),
            "아웃박스 스윕이 조립됐다 (${sweepers.joinToString()}) — 재발행은 api가 소유한다",
        )
    }
}
