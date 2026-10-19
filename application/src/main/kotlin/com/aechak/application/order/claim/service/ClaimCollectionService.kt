package com.aechak.application.order.claim.service

import com.aechak.application.order.claim.usecase.command.CompleteClaimCollectionCommand
import com.aechak.application.order.claim.usecase.command.ReshipClaimCommand
import com.aechak.application.order.claim.usecase.command.StartClaimCollectionCommand
import com.aechak.application.order.claim.usecase.result.ReshipClaimResult
import com.aechak.common.error.BusinessException
import com.aechak.domain.order.claim.Claim
import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.claim.repository.ClaimRepository
import com.aechak.domain.order.error.OrderErrorCode
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.repository.OrderRepository
import com.aechak.domain.order.shipment.CourierMaster
import com.aechak.domain.order.shipment.Shipment
import com.aechak.domain.order.shipment.enums.ShipmentType
import com.aechak.domain.order.shipment.repository.CourierMasterRepository
import com.aechak.domain.order.shipment.repository.ShipmentRepository
import org.springframework.stereotype.Service

/**
 * 반품·교환의 회수와 재발송. 회수 송장과 재발송 송장은 같은 테이블에 shipment_type으로 공존하고
 * claim_id로 클레임에 묶인다 — Facade에서만 호출된다.
 */
@Service
class ClaimCollectionService(
    private val claimRepository: ClaimRepository,
    private val orderRepository: OrderRepository,
    private val shipmentRepository: ShipmentRepository,
    private val courierMasterRepository: CourierMasterRepository,
) {
    /** 회수 송장 등록. 상태는 회수 대기(COLLECTING)에 머문다 — 물건이 돌아오기 전에는 승인이 끝나지 않는다. */
    fun startCollection(command: StartClaimCollectionCommand) {
        val claim = sellerClaim(command.claimPublicId, command.sellerId)
        if (!claim.claimType.requiresCollection()) {
            throw BusinessException(OrderErrorCode.CLAIM_COLLECTION_NOT_STARTABLE)
        }
        requireNoShipment(claim, ShipmentType.RETURN_COLLECT)
        val order = claimedOrder(claim)
        val courier = activeCourier(command.courierCode)
        claim.startCollection()
        claimRepository.save(claim)
        shipmentRepository.save(
            Shipment.dispatch(
                courier = courier,
                order = order,
                trackingNumber = command.trackingNumber,
                shipmentType = ShipmentType.RETURN_COLLECT,
                claim = claim,
            ),
        )
    }

    /**
     * 회수 완료. 이 전이가 반품 환불 집행의 전제다 — 반품 품목을 회수 완료로 바꾸고 클레임을 승인으로 닫는다.
     * 교환 품목은 교환 요청 상태로 남는다(재발송이 종결 사건이다).
     */
    fun completeCollection(command: CompleteClaimCollectionCommand) {
        val claim = sellerClaim(command.claimPublicId, command.sellerId)
        val shipment =
            shipmentRepository.findByClaimIdAndShipmentType(claim.id, ShipmentType.RETURN_COLLECT)
                ?: throw BusinessException(OrderErrorCode.CLAIM_COLLECTION_NOT_STARTABLE)
        val order = claimedOrder(claim)
        claim.completeCollection()
        shipment.markDelivered()
        if (claim.claimType == ClaimType.RETURN) {
            claimedItems(claim, order).forEach { it.markReturned() }
        }
        claimRepository.save(claim)
        shipmentRepository.save(shipment)
    }

    /** 교환 재발송. 환불이 없는 대신 이 송장 등록이 교환을 종결시킨다. */
    fun reship(command: ReshipClaimCommand): ReshipClaimResult {
        val claim = sellerClaim(command.claimPublicId, command.sellerId)
        if (!claim.isReshippable()) {
            throw BusinessException(OrderErrorCode.CLAIM_RESHIP_NOT_ALLOWED)
        }
        requireNoShipment(claim, ShipmentType.EXCHANGE_RESHIP)
        val order = claimedOrder(claim)
        val courier = activeCourier(command.courierCode)
        claim.completeExchange()
        claimRepository.save(claim)
        val shipment =
            shipmentRepository.save(
                Shipment.dispatch(
                    courier = courier,
                    order = order,
                    trackingNumber = command.trackingNumber,
                    shipmentType = ShipmentType.EXCHANGE_RESHIP,
                    claim = claim,
                ),
            )
        return ReshipClaimResult.from(shipment)
    }

    private fun requireNoShipment(
        claim: Claim,
        shipmentType: ShipmentType,
    ) {
        if (shipmentRepository.findByClaimIdAndShipmentType(claim.id, shipmentType) != null) {
            throw BusinessException(OrderErrorCode.SHIPMENT_ALREADY_REGISTERED)
        }
    }

    private fun claimedItems(
        claim: Claim,
        order: Order,
    ) = claim.claimedQuantityByOrderItemId().let { targets -> order.items.filter { it.id in targets } }

    private fun activeCourier(courierCode: String): CourierMaster =
        courierMasterRepository.findActiveByCourierCode(courierCode)
            ?: throw BusinessException(OrderErrorCode.COURIER_NOT_FOUND)

    private fun claimedOrder(claim: Claim): Order =
        orderRepository.findById(claim.orderId)
            ?: throw BusinessException(OrderErrorCode.ORDER_NOT_FOUND)

    private fun sellerClaim(
        claimPublicId: String,
        sellerId: Long,
    ): Claim =
        claimRepository.findByPublicIdAndSellerId(claimPublicId, sellerId)
            ?: throw BusinessException(OrderErrorCode.CLAIM_NOT_FOUND)
}
