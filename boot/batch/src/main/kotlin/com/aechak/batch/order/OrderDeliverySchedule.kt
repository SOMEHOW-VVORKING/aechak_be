package com.aechak.batch.order

import com.aechak.application.order.delivery.usecase.OrderDeliveryUseCase
import com.aechak.application.order.delivery.usecase.command.CompleteDeliveryBySystemCommand
import com.aechak.application.order.delivery.usecase.query.PendingDeliveryQuery
import com.aechak.application.order.delivery.usecase.result.PendingDeliveryResult
import com.aechak.batch.order.config.OrderDeliveryProperties
import com.aechak.common.error.BusinessException
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 배송 상태 자동 전이 스케줄. 택배사 추적 연동이 없는 동안 발송 시각만 보고 넘긴다 —
 * 집화 기한이 지난 건은 경고만 남기고, 배송완료 기한이 지난 건은 배송완료로 전이해 구매확정 기한을 부여한다.
 * 설정(order.delivery.enabled)이 켜져 있을 때만 조립된다.
 */
@Component
@ConditionalOnProperty(prefix = "order.delivery", name = ["enabled"], havingValue = "true")
class OrderDeliverySchedule(
    private val useCase: OrderDeliveryUseCase,
    private val properties: OrderDeliveryProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${order.delivery.cron:0 0 3 * * *}", zone = "Asia/Seoul")
    fun run() {
        val now = LocalDateTime.now(KST)
        // 두 기한 중 이른 쪽까지 거슬러 올라가 한 번만 훑고, 건별로 경고와 전이를 가른다
        val scanBefore = now.minusDays(minOf(properties.dispatchDelayDays, properties.autoCompleteDays))
        val autoCompleteBefore = now.minusDays(properties.autoCompleteDays)

        var afterShipmentId = 0L
        while (true) {
            val page = useCase.findPendingDeliveries(PendingDeliveryQuery(scanBefore, afterShipmentId, properties.scanLimit))
            if (page.isEmpty()) {
                return
            }
            page.forEach { target ->
                if (target.dispatchedAt.isBefore(autoCompleteBefore)) {
                    completeDelivery(target)
                } else {
                    log.warn("집화 지연 의심. orderPublicId={}, dispatchedAt={}", target.orderPublicId, target.dispatchedAt)
                }
            }
            // 실패한 건도 커서를 전진시킨다 — 상한에서 끊으면 앞자리 실패가 뒷건의 처리를 영구히 막는다
            afterShipmentId = page.last().shipmentId
            if (page.size < properties.scanLimit) {
                return
            }
        }
    }

    private fun completeDelivery(target: PendingDeliveryResult) {
        val deliveredAt = target.dispatchedAt.plusDays(properties.autoCompleteDays)
        try {
            useCase.completeDeliveryBySystem(CompleteDeliveryBySystemCommand(target.orderId, deliveredAt))
            log.info("배송완료 자동 전이. orderPublicId={}, deliveredAt={}", target.orderPublicId, deliveredAt)
        } catch (e: BusinessException) {
            // 스캔과 전이 사이에 셀러가 먼저 처리한 건 — 한 건의 거절이 회차를 멈추지 않게 한다
            log.warn("배송완료 자동 전이 건너뜀. orderPublicId={}, errorCode={}", target.orderPublicId, e.errorCode.code)
        } catch (e: Exception) {
            log.error("배송완료 자동 전이 실패. orderPublicId={}", target.orderPublicId, e)
        }
    }

    companion object {
        private val KST = ZoneId.of("Asia/Seoul")
    }
}
