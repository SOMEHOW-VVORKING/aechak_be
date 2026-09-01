package com.aechak.application.order.service

import com.aechak.application.order.service.model.CancelledOrderGroup
import com.aechak.application.order.usecase.result.OrderGroupDetailResult
import com.aechak.application.payment.port.PaymentGatewayStatus
import com.aechak.application.payment.port.PaymentGatewayView
import com.aechak.application.pii.port.PiiCrypto
import com.aechak.common.error.BusinessException
import com.aechak.domain.order.error.OrderErrorCode
import com.aechak.domain.order.group.DeliveryAddressSnapshot
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.group.repository.ExpiredPendingOrderGroup
import com.aechak.domain.order.group.repository.OrderGroupRepository
import com.aechak.domain.order.order.repository.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.Base64

/** 주문그룹 애그리거트 로직 보관함 — Facade에서만 호출된다 */
@Service
class OrderGroupService(
    private val orderGroupRepository: OrderGroupRepository,
    private val orderRepository: OrderRepository,
    private val piiCrypto: PiiCrypto,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 타인의 그룹인 경우 존재 여부가 노출되지 않도록 방어 */
    fun getOwnedOrderGroup(
        buyerId: Long,
        orderGroupPublicId: String,
    ): OrderGroup {
        val orderGroup =
            orderGroupRepository.findByPublicId(orderGroupPublicId)
                ?: throw BusinessException(OrderErrorCode.ORDER_GROUP_NOT_FOUND)
        if (orderGroup.buyerId != buyerId) {
            log.warn("타인의 주문그룹 조회 시도. orderGroupPublicId={}, buyerId={}", orderGroupPublicId, buyerId)
            throw BusinessException(OrderErrorCode.ORDER_GROUP_NOT_FOUND)
        }
        return orderGroup
    }

    fun findExpiredPendingTargets(
        now: LocalDateTime,
        after: ExpiredPendingOrderGroup?,
        limit: Int,
    ): List<ExpiredPendingOrderGroup> = orderGroupRepository.findExpiredPendingTargets(now, after, limit)

    /**
     * true: 결제가 존재하지 않거나 진행되지 않은 경우.
     * false: 결제가 진행 중이거나 완료된 경우
     */
    fun isUnpaid(
        orderGroupPublicId: String,
        gatewayView: PaymentGatewayView?,
    ): Boolean {
        if (gatewayView == null) return true
        return when (gatewayView.status) {
            PaymentGatewayStatus.READY,
            PaymentGatewayStatus.FAILED,
            PaymentGatewayStatus.CANCELLED,
            -> {
                true
            }

            PaymentGatewayStatus.PAID,
            PaymentGatewayStatus.PARTIAL_CANCELLED,
            -> {
                log.warn("승인된 결제가 남은 만료 그룹을 건너뜀. orderGroupPublicId={}, status={}", orderGroupPublicId, gatewayView.status)
                false
            }

            PaymentGatewayStatus.PAY_PENDING, // 곧 PAID나 FAILED로 확정되니 스킵
            PaymentGatewayStatus.VIRTUAL_ACCOUNT_ISSUED, // MVP에선 제공하지 않음. 포트원의 입금 기한이 지나면 FAILED로 전환되니 스킵
            -> {
                log.info("결제 진행 중인 만료 그룹을 건너뜀. orderGroupPublicId={}, status={}", orderGroupPublicId, gatewayView.status)
                false
            }
        }
    }

    fun cancelUnpaidGroup(orderGroupId: Long): CancelledOrderGroup? {
        if (!orderGroupRepository.cancelIfPending(orderGroupId)) return null
        val orderGroup = orderGroupRepository.findById(orderGroupId) ?: return null
        val orders = orderRepository.findAllByOrderGroupIdWithItems(orderGroupId)
        orders.forEach { it.cancelUnpaid() }
        return CancelledOrderGroup(
            publicId = orderGroup.publicId,
            buyerId = orderGroup.buyerId,
            usedPoint = orderGroup.usedPoint,
            items = orders.flatMap { it.items }.map { CancelledOrderGroup.Item(it.optionCombinationId, it.quantity) },
        )
    }

    fun decryptDeliveryAddress(snapshot: DeliveryAddressSnapshot): OrderGroupDetailResult.DeliveryAddressSnapshotResult =
        OrderGroupDetailResult.DeliveryAddressSnapshotResult(
            receiverName = decrypt(snapshot.receiverNameEnc),
            contactNumber = decrypt(snapshot.contactNumberEnc),
            zipCode = snapshot.zipCode,
            baseAddress = snapshot.baseAddress,
            detailAddress = snapshot.detailAddress,
            deliveryMemo = snapshot.deliveryMemo,
        )

    private fun decrypt(cipher: String): String = piiCrypto.decrypt(Base64.getDecoder().decode(cipher))
}
