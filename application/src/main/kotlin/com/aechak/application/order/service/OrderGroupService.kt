package com.aechak.application.order.service

import com.aechak.application.order.usecase.result.OrderGroupDetailResult
import com.aechak.application.pii.port.PiiCrypto
import com.aechak.common.error.BusinessException
import com.aechak.domain.order.error.OrderErrorCode
import com.aechak.domain.order.group.DeliveryAddressSnapshot
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.group.repository.OrderGroupRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.Base64

/** 주문그룹 애그리거트 로직 보관함 — Facade에서만 호출된다 */
@Service
class OrderGroupService(
    private val orderGroupRepository: OrderGroupRepository,
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
