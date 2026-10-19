package com.aechak.batch.order

import com.aechak.application.order.usecase.OrderUseCase
import com.aechak.application.order.usecase.command.AutoConfirmPurchaseCommand
import com.aechak.application.order.usecase.command.MarkConfirmNotifiedCommand
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
 * 회차 안에서 id 커서를 밀며 대상을 전부 처리하고, 건별로 격리해 앞자리 실패가 뒷건을 막지 않게 한다.
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
        // 회차 전체가 한 시각을 기준으로 판정한다 — 스캔과 확정의 기준이 갈리면 경계 주문이 양쪽에서 빠진다
        val now = LocalDateTime.now(KST)
        val confirmed = autoConfirm(now)
        val notified = notifyUpcoming(now)
        log.info("구매확정 자동 배치 종료. now={}, confirmed={}, notified={}", now, confirmed, notified)
    }

    private fun autoConfirm(now: LocalDateTime): Int =
        sweep(
            step = STEP_CONFIRM,
            scan = { cursor ->
                orderUseCase.findAutoConfirmTargetIds(
                    AutoConfirmScanQuery(dueAtOrBefore = now, afterOrderId = cursor, limit = properties.scanSize),
                )
            },
            handle = { orderId ->
                orderUseCase.autoConfirmPurchase(AutoConfirmPurchaseCommand(orderId, now))
                true
            },
        )

    private fun notifyUpcoming(now: LocalDateTime): Int =
        sweep(
            step = STEP_NOTIFY,
            scan = { cursor ->
                orderUseCase.findConfirmNotifyTargetIds(
                    ConfirmNotifyScanQuery(
                        dueAtAfter = now,
                        dueAtOrBefore = now.plusDays(properties.notifyLeadDays),
                        afterOrderId = cursor,
                        limit = properties.scanSize,
                    ),
                )
            },
            handle = { orderId -> orderUseCase.markConfirmNotified(MarkConfirmNotifiedCommand(orderId, now)) },
        )

    /**
     * 빈 페이지가 나올 때까지 커서를 밀며 전부 처리한다 — 처리한 건은 스캔 조건에서 빠지지만,
     * 유보·실패로 남은 건이 다음 페이지를 가리지 않게 커서를 쓴다. 처리 성공 건수를 돌려준다.
     */
    private fun sweep(
        step: String,
        scan: (Long) -> List<Long>,
        handle: (Long) -> Boolean,
    ): Int {
        var cursor = 0L
        var done = 0
        while (true) {
            val targets = scan(cursor)
            if (targets.isEmpty()) {
                return done
            }
            targets.forEach { orderId ->
                if (isolate(step, orderId) { handle(orderId) }) {
                    done++
                }
            }
            val next = targets.last()
            if (next <= cursor) {
                // 스캔이 id 오름차순을 지키지 않으면 같은 페이지를 무한히 읽는다
                log.error("{} 커서가 전진하지 않아 회차를 중단한다. cursor={}, next={}", step, cursor, next)
                return done
            }
            cursor = next
        }
    }

    /** 건별 격리 — 유보(BusinessException)는 스캔과 처리 사이에 상태가 바뀐 정상 흐름이라 로그만 남긴다. */
    private fun isolate(
        step: String,
        orderId: Long,
        action: () -> Boolean,
    ): Boolean =
        try {
            action()
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
