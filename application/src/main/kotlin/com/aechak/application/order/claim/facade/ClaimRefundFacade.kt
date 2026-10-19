package com.aechak.application.order.claim.facade

import com.aechak.application.order.claim.service.ClaimRefundService
import com.aechak.application.order.claim.usecase.ClaimRefundUseCase
import com.aechak.application.order.claim.usecase.command.ExecuteClaimRefundCommand
import com.aechak.application.order.claim.usecase.result.ExecuteClaimRefundResult
import com.aechak.application.order.claim.usecase.result.RefundPendingClaimResult
import com.aechak.application.payment.port.PaymentCancelRequester
import com.aechak.application.payment.usecase.PaymentUseCase
import com.aechak.application.payment.usecase.command.CancelPaymentCommand
import com.aechak.common.error.BusinessException
import com.aechak.domain.order.error.OrderErrorCode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

/**
 * 환불 집행 계약의 유일한 구현체. 셀러 자격 검증을 주입받지 않아 ClaimFacade와 파사드를 갈랐다 —
 * 집행은 배치가 부르고, 셀러 자격을 끌고 오면 배치가 쓰지도 않는 의존을 조립해야 한다.
 *
 * 경계가 셋인 이유는 가운데에 PG 호출이 있기 때문이다.
 * ① 환불 요청 상태 선점(커밋) → ② 트랜잭션 밖 PG 취소 → ③ 원장 차감·주문그룹 전이·자산 복원(한 트랜잭션).
 * ①을 커밋하지 않고 PG를 부르면 같은 클레임이 두 번 환불될 수 있고, ③을 쪼개면 대사가 미동기화로 판정한다.
 */
@Service
class ClaimRefundFacade(
    private val claimRefundService: ClaimRefundService,
    private val paymentUseCase: PaymentUseCase,
    transactionManager: PlatformTransactionManager,
) : ClaimRefundUseCase {
    private val log = LoggerFactory.getLogger(javaClass)

    private val tx = TransactionTemplate(transactionManager)

    @Transactional(readOnly = true)
    override fun findRefundPendingClaims(limit: Int): List<RefundPendingClaimResult> = claimRefundService.findRefundPending(limit)

    override fun executeRefund(command: ExecuteClaimRefundCommand): ExecuteClaimRefundResult {
        val reservation =
            tx.execute { claimRefundService.reserve(command.claimPublicId) }
                ?: return ExecuteClaimRefundResult.skipped()

        val gateway =
            try {
                paymentUseCase.requestCancelAtGateway(
                    CancelPaymentCommand(
                        orderGroupId = reservation.orderGroupId,
                        amount = reservation.refundAmount,
                        reason = CANCEL_REASON,
                        requester = PaymentCancelRequester.CUSTOMER,
                    ),
                )
            } catch (e: RuntimeException) {
                // 선점은 결제 쪽이 이미 풀었다 — 클레임도 실패로 남겨 스캔에서 빼고 운영자 판단으로 넘긴다
                markFailed(command.claimPublicId, e)
                throw BusinessException(OrderErrorCode.CLAIM_REFUND_FAILED, e)
            }

        val confirmed =
            gateway.confirmedAmount ?: run {
                log.info(
                    "PG가 환불을 접수만 함 — 원장·주문그룹 보류, 클레임은 요청 상태로 남김. claimPublicId={}, refundAmount={}",
                    reservation.claimPublicId,
                    reservation.refundAmount,
                )
                return ExecuteClaimRefundResult.requested(reservation.refundAmount)
            }

        return tx.execute { claimRefundService.settle(reservation.claimPublicId, confirmed) }!!
    }

    /** 실패 기록은 보상 동작이라 실패해도 원래 예외를 가리지 않는다 — 남은 요청 상태는 대사가 본다 */
    private fun markFailed(
        claimPublicId: String,
        cause: RuntimeException,
    ) {
        runCatching { tx.execute { claimRefundService.markFailed(claimPublicId) } }
            .onFailure { log.error("환불 실패 기록에 실패 — 클레임이 요청 상태로 남음. claimPublicId={}", claimPublicId, it) }
        log.error("환불 집행 실패. claimPublicId={}", claimPublicId, cause)
    }

    companion object {
        private const val CANCEL_REASON = "구매자 취소 요청 승인"
    }
}
