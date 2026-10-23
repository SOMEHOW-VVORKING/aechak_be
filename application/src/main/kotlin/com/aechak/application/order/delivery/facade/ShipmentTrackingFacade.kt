package com.aechak.application.order.delivery.facade

import com.aechak.application.order.delivery.port.ShipmentTrackingPort
import com.aechak.application.order.delivery.service.OrderDeliveryService
import com.aechak.application.order.delivery.usecase.ShipmentTrackingUseCase
import com.aechak.application.order.delivery.usecase.command.SyncShipmentTrackingCommand
import com.aechak.application.order.delivery.usecase.result.ShipmentTrackingSyncResult
import com.aechak.application.order.delivery.usecase.result.ShipmentTrackingTargetResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

/**
 * 추적 갱신 유스케이스 구현. 배송완료 전이 파사드(OrderDeliveryFacade)와 입구를 나눈 이유는
 * 이쪽만 외부 포트를 요구하기 때문이다.
 *
 * 택배사 호출은 트랜잭션 밖이다 — 외부 응답을 기다리는 동안 커넥션을 쥐면 풀이 마른다.
 * 그래서 갱신 메서드에는 선언형 트랜잭션을 걸지 않고 반영 구간만 프로그램형으로 연다.
 *
 * 포트를 선택 주입으로 받는다 — 추적 어댑터는 배치 모듈에만 조립되지만 이 패키지는
 * 구매자·셀러 실행 모듈도 스캔하므로, 필수 주입이면 어댑터가 없는 쪽의 부팅이 깨진다.
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
    override fun findTrackingTargets(
        afterShipmentId: Long,
        limit: Int,
    ): List<ShipmentTrackingTargetResult> =
        orderDeliveryService
            .findTrackingTargets(afterShipmentId, limit)
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
