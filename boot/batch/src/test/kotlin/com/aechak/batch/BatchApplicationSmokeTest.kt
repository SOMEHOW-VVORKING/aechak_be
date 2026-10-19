package com.aechak.batch

import com.aechak.application.order.claim.usecase.ClaimAutoApproveUseCase
import com.aechak.application.order.claim.usecase.ClaimRefundUseCase
import com.aechak.application.order.usecase.OrderExpiryUseCase
import com.aechak.application.payment.port.PaymentReconciliationSourcePort
import com.aechak.application.payment.reconciliation.usecase.PaymentReconciliationUseCase
import com.aechak.batch.order.ClaimAutoApproveSchedule
import com.aechak.batch.order.ClaimRefundSchedule
import com.aechak.batch.order.OrderExpirySchedule
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
    fun `만료 스케줄과 그 의존이 조립된다`() {
        // 선별 스캔이라 order BC 파사드가 요구하는 어댑터가 빠지면 여기서 깨진다
        context.getBean(OrderExpirySchedule::class.java)
        context.getBean(OrderExpiryUseCase::class.java)
    }

    @Test
    fun `환불 집행 스케줄과 그 의존이 조립되고 셀러 자격을 요구하는 클레임 파사드는 빠진다`() {
        // 클레임 제외 필터가 패키지 단위로 돌아가면 환불 집행 파사드까지 빠져 이 스케줄이 조립되지 않는다
        context.getBean(ClaimRefundSchedule::class.java)
        context.getBean(ClaimRefundUseCase::class.java)
        assertFalse(context.containsBean("claimFacade"), "셀러 자격 검증을 요구하는 클레임 파사드가 배치에 섞이면 안 된다")
    }

    @Test
    fun `자동승인 스케줄과 그 의존이 조립된다`() {
        // 자동승인은 환불 집행 계약까지 함께 부른다 — 둘 중 하나라도 빠지면 기한 넘긴 클레임이 멈춰 선다
        context.getBean(ClaimAutoApproveSchedule::class.java)
        context.getBean(ClaimAutoApproveUseCase::class.java)
    }

    @Test
    fun `어드민 전용 빈은 배치에 조립되지 않는다`() {
        assertFalse(context.containsBean("adminSellerReviewFacade"), "Admin* 제외 필터가 지워지면 어드민 파사드가 배치에 섞인다")
        assertTrue(context.containsBean("paymentReconciliationFacade"))
    }
}
