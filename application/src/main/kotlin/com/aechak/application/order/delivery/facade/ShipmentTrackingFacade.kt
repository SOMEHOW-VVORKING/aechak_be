package com.aechak.application.order.delivery.facade

import com.aechak.application.order.delivery.port.ShipmentTrackingPort
import com.aechak.application.order.delivery.service.OrderDeliveryService
import com.aechak.application.order.delivery.usecase.ShipmentTrackingUseCase
import com.aechak.application.order.delivery.usecase.command.SyncShipmentTrackingCommand
import com.aechak.application.order.delivery.usecase.query.ShipmentTrackingScanQuery
import com.aechak.application.order.delivery.usecase.result.ShipmentTrackingSyncResult
import com.aechak.application.order.delivery.usecase.result.ShipmentTrackingTargetResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

/**
 * 추적 갱신 유스케이스 구현. 택배사 호출은 트랜잭션 밖이고 반영 구간만 프로그램형으로 연다.
 * 포트는 선택 주입이다 — 추적 어댑터가 조립되지 않은 실행 모듈에서도 이 파사드가 떠야 하고,
 * 그때는 갱신하지 않고 어댑터 부재를 결과로 알린다.
 */
@Service
class ShipmentTrackingFacade(
    private val orderDeliveryService: OrderDeliveryService,
    private val trackingPort: ShipmentTrackingPort?,
    transactionManager: PlatformTransactionManager,
) : ShipmentTrackingUseCase {
    private val log = LoggerFactory.getLogger(javaClass)
    private val tx = TransactionTemplate(transactionManager)

    @Transactional(readOnly = true)
    override fun findTrackingTargets(query: ShipmentTrackingScanQuery): List<ShipmentTrackingTargetResult> =
        orderDeliveryService
            .findTrackingTargets(query.afterShipmentId, query.limit)
            .map(ShipmentTrackingTargetResult::from)

    override fun syncTracking(command: SyncShipmentTrackingCommand): ShipmentTrackingSyncResult {
        val port =
            trackingPort ?: run {
                log.error("추적 어댑터가 조립되지 않아 갱신을 건너뜀. shipmentId={}", command.shipmentId)
                return ShipmentTrackingSyncResult.noAdapter(command.shipmentId)
            }
        val snapshot =
            port.fetchTracking(command.courierCode, command.trackingNumber)
                ?: return ShipmentTrackingSyncResult.notFound(command.shipmentId)
        return tx.execute {
            // 담긴 문구를 결과로 돌려준다 — 도메인이 공백·길이를 정리하므로 원본과 다를 수 있다
            val applied = orderDeliveryService.applyTracking(command.shipmentId, snapshot.lastStatus)
            ShipmentTrackingSyncResult.updated(command.shipmentId, applied)
        }!!
    }
}
