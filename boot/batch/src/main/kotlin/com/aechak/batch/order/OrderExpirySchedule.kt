package com.aechak.batch.order

import com.aechak.application.order.usecase.OrderExpiryUseCase
import com.aechak.application.order.usecase.command.ExpireOrderGroupCommand
import com.aechak.application.order.usecase.query.ExpiredOrderGroupQuery
import com.aechak.application.order.usecase.result.ExpiredOrderGroupResult
import com.aechak.application.payment.usecase.PaymentUseCase
import com.aechak.application.payment.usecase.command.CompletePaymentByGatewayCommand
import com.aechak.application.payment.usecase.result.CompletePaymentStatus
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 결제되지 않은 주문그룹의 만료 처리. 주문그룹 생성이 재고와 적립금을 먼저 깎으므로 만료 건을 방치하면 그만큼 자산이 묶인다.
 *
 * 건마다 두 단계로 돈다. 먼저 결제 결과를 확정해(외부 조회라 트랜잭션 밖) 승인·승인대기 건을 걸러내고,
 * 그 뒤에도 결제대기로 남은 건만 취소·복원한다. 돈이 나간 그룹을 취소하지 않으려면 이 순서여야 한다.
 *
 * 한 회차는 커서로 페이지를 넘기며 최대 max-pages 페이지까지 보고, 상한에 걸리면 커서를 들고 있다가 다음 회차가 그 뒤부터 이어 간다.
 * 끝에 닿으면 커서를 비워 다음 회차가 처음부터 다시 훑는다. 처리할 수 없는 건이 아무리 쌓여도 뒤의 만료 주문이 가려지지 않는다.
 *
 * 환불 배선이 아직 없어 기본값은 꺼져 있다 — 승인과 경합에서 취소가 이기면 수동 환불이 필요한 건이 남는다.
 */
@Component
@ConditionalOnProperty(name = ["order.expiry.enabled"], havingValue = "true")
class OrderExpirySchedule(
    private val orderExpiryUseCase: OrderExpiryUseCase,
    private val paymentUseCase: PaymentUseCase,
    @Value("\${order.expiry.page-size:100}") private val pageSize: Int,
    @Value("\${order.expiry.max-pages:10}") private val maxPages: Int,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 회차 간 스캔 위치. 스케줄러 풀의 스레드가 회차마다 달라질 수 있어 @Volatile이다 */
    @Volatile
    private var cursor: ExpiredOrderGroupResult? = null

    @Scheduled(cron = "\${order.expiry.cron:0 * * * * *}", zone = "Asia/Seoul")
    fun run() {
        var examined = 0
        repeat(maxPages) {
            val candidates = orderExpiryUseCase.findExpiredOrderGroups(queryAfter(cursor))
            if (candidates.isEmpty()) return restart(examined)
            candidates.forEach { candidate ->
                try {
                    expire(candidate)
                } catch (e: Exception) {
                    // 한 건의 실패가 회차를 멈추지 않게 건별로 격리한다. 결제 상태를 모르는 건은 취소하지 않고 넘어간다
                    log.error("주문서 만료 실패 — 이번 회차는 건너뜀. orderGroupPublicId={}", candidate.orderGroupPublicId, e)
                }
            }
            examined += candidates.size
            cursor = candidates.last()
            if (candidates.size < pageSize) return restart(examined)
        }
        log.warn("주문서 만료 회차가 페이지 상한에 걸림 — 다음 회차가 커서 뒤부터 이어 간다. 살펴본건={}", examined)
    }

    /** 끝까지 훑었으니 다음 회차는 처음부터 — 커서를 들고 있으면 그 앞에 새로 만료된 건을 못 본다 */
    private fun restart(examined: Int) {
        cursor = null
        if (examined > 0) log.info("주문서 만료 회차 종료. 살펴본건={}", examined)
    }

    private fun queryAfter(cursor: ExpiredOrderGroupResult?): ExpiredOrderGroupQuery =
        ExpiredOrderGroupQuery(
            limit = pageSize,
            afterExpiresAt = cursor?.expiresAt,
            afterOrderGroupId = cursor?.orderGroupId,
        )

    private fun expire(candidate: ExpiredOrderGroupResult) {
        val settlement = settlePayment(candidate)
        if (settlement == CompletePaymentStatus.PAID || settlement == CompletePaymentStatus.IN_PROGRESS) {
            log.info(
                "결제가 진행된 주문그룹이라 만료시키지 않음. orderGroupPublicId={}, 결제상태={}",
                candidate.orderGroupPublicId,
                settlement,
            )
            return
        }
        val result = orderExpiryUseCase.expireOrderGroup(ExpireOrderGroupCommand(candidate.orderGroupId))
        log.info(
            "주문서 만료 처리. orderGroupPublicId={}, 결과={}, 취소주문={}건, 재고복원={}건(실패 {}건), 적립금복원={}원",
            candidate.orderGroupPublicId,
            result.status,
            result.cancelledOrders,
            result.restoredStockItems,
            result.failedStockItems,
            result.releasedPoint,
        )
    }

    /** 결제 식별자는 주문그룹 publicId다. 결제를 시도한 흔적이 없으면 null이고 게이트웨이 조회도 일어나지 않는다 */
    private fun settlePayment(candidate: ExpiredOrderGroupResult): CompletePaymentStatus? =
        paymentUseCase
            .completePaymentByGateway(CompletePaymentByGatewayCommand(candidate.orderGroupPublicId))
            ?.status
}
