package com.aechak.batch.order

import com.aechak.application.order.claim.usecase.ClaimAutoApproveUseCase
import com.aechak.application.order.claim.usecase.ClaimRefundUseCase
import com.aechak.application.order.claim.usecase.command.AutoApproveClaimCommand
import com.aechak.application.order.claim.usecase.command.ExecuteClaimRefundCommand
import com.aechak.application.order.claim.usecase.result.AutoApprovableClaimResult
import com.aechak.application.order.claim.usecase.result.AutoApproveClaimResult
import com.aechak.application.order.claim.usecase.result.ExecuteClaimRefundResult
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 자동승인 스케줄의 조립 조건. 이 스케줄은 승인에서 끝나지 않고 환불까지 집행하므로
 * 환불 스위치가 닫힌 환경에서 혼자 켜지면 돈을 내보내지 않기로 한 결정을 우회한다.
 * 컨텍스트를 띄우지 않고 조건만 본다 — 스위치 조합마다 통합 컨텍스트를 띄울 이유가 없다.
 */
class ClaimAutoApproveScheduleConditionTest {
    private val runner =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration::class.java))
            .withBean(ClaimAutoApproveUseCase::class.java, { NoopAutoApprove() })
            .withBean(ClaimRefundUseCase::class.java, { NoopRefund() })
            .withUserConfiguration(ClaimAutoApproveSchedule::class.java)

    @Test
    fun `두 스위치가 모두 켜져야 자동승인 스케줄이 조립된다`() {
        runner
            .withPropertyValues("order.claim.refund.enabled=true", "order.claim.auto-approve.enabled=true")
            .run { context -> assertEquals(1, scheduleCount(context)) }
    }

    @Test
    fun `환불 스위치가 꺼져 있으면 자동승인만 켜도 조립되지 않는다`() {
        runner
            .withPropertyValues("order.claim.refund.enabled=false", "order.claim.auto-approve.enabled=true")
            .run { context ->
                assertEquals(0, scheduleCount(context), "환불이 닫힌 환경에서 자동승인이 돈을 내보내면 안 된다")
            }
    }

    @Test
    fun `자동승인 스위치가 꺼져 있으면 환불이 켜져 있어도 조립되지 않는다`() {
        runner
            .withPropertyValues("order.claim.refund.enabled=true", "order.claim.auto-approve.enabled=false")
            .run { context -> assertEquals(0, scheduleCount(context)) }
    }

    @Test
    fun `스위치를 적지 않으면 조립되지 않는다`() {
        runner.run { context -> assertEquals(0, scheduleCount(context)) }
    }

    private fun scheduleCount(context: org.springframework.context.ApplicationContext): Int =
        context.getBeanNamesForType(ClaimAutoApproveSchedule::class.java).size

    private class NoopAutoApprove : ClaimAutoApproveUseCase {
        override fun findAutoApprovableClaims(limit: Int): List<AutoApprovableClaimResult> = emptyList()

        override fun autoApprove(command: AutoApproveClaimCommand): AutoApproveClaimResult = AutoApproveClaimResult.skipped()
    }

    private class NoopRefund : ClaimRefundUseCase {
        override fun findRefundPendingClaims(limit: Int) = emptyList<Nothing>()

        override fun executeRefund(command: ExecuteClaimRefundCommand): ExecuteClaimRefundResult = ExecuteClaimRefundResult.skipped()
    }
}
