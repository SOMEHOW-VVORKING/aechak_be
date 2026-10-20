package com.aechak.batch.order

import com.aechak.application.order.delivery.usecase.ShipmentTrackingUseCase
import com.aechak.application.order.delivery.usecase.result.ShipmentTrackingSyncOutcome
import com.aechak.application.order.delivery.usecase.result.ShipmentTrackingTargetResult
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 택배사 추적 갱신 스케줄. 발송됐고 아직 배송완료가 아닌 최초 발송을 훑어 추적 상태를 채운다.
 * 상태 전이는 일으키지 않는다 — 배송완료 전이는 OrderDeliverySchedule과 셀러 수동 처리의 몫이다.
 *
 * 설정(order.delivery.tracking.enabled)이 켜져 있을 때만 조립되고 **기본값은 꺼짐**이다.
 * 지금 붙을 수 있는 추적 어댑터가 목뿐이어서, 켜면 표식이 붙은 가짜 상태가 DB에 쌓인다.
 */
@Component
@ConditionalOnProperty(prefix = "order.delivery.tracking", name = ["enabled"], havingValue = "true")
class ShipmentTrackingSchedule(
    private val useCase: ShipmentTrackingUseCase,
    private val properties: ShipmentTrackingProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${order.delivery.tracking.cron:0 0 */6 * * *}", zone = "Asia/Seoul")
    fun run() {
        var afterShipmentId = 0L
        var updated = 0
        var notFound = 0
        var failed = 0
        while (true) {
            val page = useCase.findTrackingTargets(afterShipmentId, properties.scanLimit)
            if (page.isEmpty()) {
                break
            }
            for (target in page) {
                when (sync(target)) {
                    ShipmentTrackingSyncOutcome.UPDATED -> {
                        updated++
                    }

                    ShipmentTrackingSyncOutcome.TRACKING_NOT_FOUND -> {
                        notFound++
                    }

                    ShipmentTrackingSyncOutcome.NO_ADAPTER -> {
                        // 어댑터가 없으면 남은 건도 전부 같은 결과다 — 건마다 로그를 쌓지 않고 회차를 끊는다
                        log.warn("추적 어댑터가 없어 회차를 중단한다. 갱신={}건", updated)
                        return
                    }

                    null -> {
                        failed++
                    }
                }
            }
            // 실패한 건도 커서를 전진시킨다 — 상한에서 끊으면 앞자리 실패가 뒷건의 처리를 영구히 막는다
            afterShipmentId = page.last().shipmentId
            if (page.size < properties.scanLimit) {
                break
            }
        }
        log.info("택배사 추적 갱신 종료. 갱신={}건, 미조회={}건, 실패={}건", updated, notFound, failed)
    }

    /** 건별 격리 — 한 송장의 조회 실패가 회차를 멈추지 않게 한다. 실패는 null로 알린다. */
    private fun sync(target: ShipmentTrackingTargetResult): ShipmentTrackingSyncOutcome? =
        try {
            useCase.syncTracking(target.toCommand()).outcome
        } catch (e: Exception) {
            log.warn("추적 갱신 실패 — 다음 건으로 넘어감. orderPublicId={}", target.orderPublicId, e)
            null
        }
}
