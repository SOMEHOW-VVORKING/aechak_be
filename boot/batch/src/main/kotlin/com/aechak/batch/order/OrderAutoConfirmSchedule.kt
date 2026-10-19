package com.aechak.batch.order

import com.aechak.application.order.usecase.OrderUseCase
import com.aechak.application.order.usecase.query.AutoConfirmScanQuery
import com.aechak.application.order.usecase.query.ConfirmNotifyScanQuery
import com.aechak.common.error.BusinessException
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 구매확정 자동 배치. 기한이 지난 배송완료 주문을 확정하고, 기한이 다가온 주문에는 사전 안내 기록을 남긴다.
 * 확정은 비가역이고 반품 기한·정산의 기산점이라 진행 중 클레임이 있는 주문은 스캔에서 빠진다.
 *
 * 회차 안에서 커서를 밀며 대상을 전부 처리하고, 건별로 격리해 앞자리 실패가 뒷건을 막지 않게 한다.
 */
@Component
class OrderAutoConfirmSchedule(
    private val orderUseCase: OrderUseCase,
    private val properties: OrderAutoConfirmProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${order.auto-confirm.cron:0 0 2 * * *}", zone = KST_ID)
    fun runDaily() {
        if (!properties.enabled) {
            return
        }
        val now = LocalDateTime.now(KST)
        val confirmed = autoConfirm(now)
        val notified = notifyUpcoming(now)
        log.info("구매확정 자동 배치 종료. now={}, confirmed={}, notified={}", now, confirmed, notified)
    }

    private fun autoConfirm(now: LocalDateTime): Int {
        var cursor = 0L
        var confirmed = 0
        var round = 0
        while (round < properties.maxRounds) {
            val targets =
                orderUseCase.findAutoConfirmTargetIds(
                    AutoConfirmScanQuery(dueAtOrBefore = now, afterOrderId = cursor, limit = properties.scanSize),
                )
            if (targets.isEmpty()) {
                return confirmed
            }
            targets.forEach { orderId ->
                if (isolate(STEP_CONFIRM, orderId) { orderUseCase.autoConfirmPurchase(orderId) }) {
                    confirmed++
                }
            }
            cursor = targets.last()
            round++
        }
        log.warn("자동확정 회차 상한에 걸려 남은 대상을 다음 회차로 넘긴다. cursor={}, confirmed={}", cursor, confirmed)
        return confirmed
    }

    private fun notifyUpcoming(now: LocalDateTime): Int {
        var cursor = 0L
        var notified = 0
        var round = 0
        while (round < properties.maxRounds) {
            val targets =
                orderUseCase.findConfirmNotifyTargetIds(
                    ConfirmNotifyScanQuery(
                        dueAtAfter = now,
                        dueAtOrBefore = now.plusDays(properties.notifyLeadDays),
                        afterOrderId = cursor,
                        limit = properties.scanSize,
                    ),
                )
            if (targets.isEmpty()) {
                return notified
            }
            targets.forEach { orderId ->
                if (isolate(STEP_NOTIFY, orderId) { orderUseCase.markConfirmNotified(orderId) }) {
                    notified++
                }
            }
            cursor = targets.last()
            round++
        }
        log.warn("사전 안내 회차 상한에 걸려 남은 대상을 다음 회차로 넘긴다. cursor={}, notified={}", cursor, notified)
        return notified
    }

    /** 건별 격리 — 유보(BusinessException)는 스캔과 처리 사이에 상태가 바뀐 정상 흐름이라 로그만 남긴다. */
    private fun isolate(
        step: String,
        orderId: Long,
        action: () -> Unit,
    ): Boolean =
        try {
            action()
            true
        } catch (e: BusinessException) {
            log.info("{} 유보. orderId={}, errorCode={}", step, orderId, e.errorCode.code)
            false
        } catch (e: Exception) {
            log.error("{} 실패. orderId={}", step, orderId, e)
            false
        }

    companion object {
        private const val KST_ID = "Asia/Seoul"
        private const val STEP_CONFIRM = "자동확정"
        private const val STEP_NOTIFY = "구매확정 사전 안내"
        private val KST = ZoneId.of(KST_ID)
    }
}
