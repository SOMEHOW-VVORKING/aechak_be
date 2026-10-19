package com.aechak.batch

import com.aechak.application.order.usecase.OrderUseCase
import com.aechak.application.payment.port.PaymentReconciliationSourcePort
import com.aechak.application.payment.reconciliation.usecase.PaymentReconciliationUseCase
import com.aechak.application.settlement.seller.usecase.SellerSettlementUseCase
import com.aechak.application.settlement.usecase.SettlementUseCase
import com.aechak.batch.payment.PaymentReconciliationSchedule
import com.aechak.batch.support.BatchIntegrationTestBase
import com.aechak.domain.payment.reconciliation.ReconciliationSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 조립 스모크. 선별 스캔이라 application 패키지가 늘 때마다 어댑터 누락으로 부팅이 깨질 수 있다 —
 * 대사 스케줄과 그 의존(확정 본체·포트원 소스)이 실제로 조립되는지, 어드민 전용 빈은 빠지는지 고정한다.
 */
class BatchApplicationSmokeTest : BatchIntegrationTestBase() {
    @Autowired
    private lateinit var context: ApplicationContext

    @Test
    fun `대사 스케줄과 유스케이스와 포트원 소스가 조립된다`() {
        context.getBean(PaymentReconciliationSchedule::class.java)
        context.getBean(PaymentReconciliationUseCase::class.java)
        val sources = context.getBeansOfType(PaymentReconciliationSourcePort::class.java).values.map { it.source }

        assertEquals(listOf(ReconciliationSource.PORTONE), sources, "토스 소스는 설정으로 켜기 전엔 조립되지 않아야 한다")
    }

    @Test
    fun `구매확정 주문 파사드와 그 정산 의존이 조립된다`() {
        // 확정이 정산 원장을 만들게 되면서 OrderUseCase가 SettlementUseCase를 요구한다 —
        // 스캔에서 application.settlement가 빠지면 자동확정 배치가 부팅에서 깨진다
        context.getBean(OrderUseCase::class.java)
        context.getBean(SettlementUseCase::class.java)
    }

    @Test
    fun `셀러센터 전용 정산 조회는 배치에 조립되지 않는다`() {
        // 구매확정 원장 생성 때문에 settlement BC를 스캔하는데, 셀러 조회 파사드는 셀러 자격(application.seller)을
        // 요구한다 — 제외 필터가 지워지면 배치가 쓰지도 않는 의존 때문에 부팅에서 깨진다
        val names = context.getBeanNamesForType(SellerSettlementUseCase::class.java)

        assertTrue(names.isEmpty(), "셀러 전용 정산 조회가 배치에 조립됐다 (${names.joinToString()})")
    }

    @Test
    fun `어드민 전용 빈은 배치에 조립되지 않는다`() {
        assertFalse(context.containsBean("adminSellerReviewFacade"), "Admin* 제외 필터가 지워지면 어드민 파사드가 배치에 섞인다")
        assertTrue(context.containsBean("paymentReconciliationFacade"))
    }
}
