package com.aechak.batch.order

import com.aechak.domain.order.group.DeliveryAddressSnapshot
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.OrderItem
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.order.shipment.CourierMaster
import com.aechak.domain.order.shipment.Shipment
import com.aechak.domain.order.shipment.enums.ShipmentType
import jakarta.persistence.EntityManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * 추적 갱신 배치 테스트가 공유하는 대상 심기. 어댑터가 있는 경우와 없는 경우는 컨텍스트 설정이 달라
 * 테스트 클래스가 나뉘는데, 대상 선별 조건(배송중 주문의 미배송 최초 발송)은 양쪽이 같아야 한다.
 */
internal object ShipmentTrackingFixture {
    const val COURIER_CODE = "CJ"

    /** DATETIME(6)은 마이크로초까지만 담으므로 심기 전에 잘라 둔다 — 나노초 시계에서 심은 값과 읽은 값이 어긋난다. */
    fun now(): LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS)

    /** 테이블은 테스트마다 비워지므로 택배사 마스터도 매번 다시 심는다. */
    fun seedCourier(
        em: EntityManager,
        tx: TransactionTemplate,
    ) {
        tx.execute { em.persist(CourierMaster.register(courierCode = COURIER_CODE, courierName = "씨제이대한통운")) }
    }

    /** 배송중 주문 하나와 그 최초 발송을 심고 주문 공개 식별자를 돌려준다. */
    fun seedShippingOrder(
        em: EntityManager,
        tx: TransactionTemplate,
        trackingNumber: String,
    ): String =
        tx.execute {
            val group =
                OrderGroup.create(
                    buyerId = 9_999L,
                    deliveryAddressId = 0L,
                    deliveryAddress =
                        DeliveryAddressSnapshot(
                            receiverNameEnc = "enc-receiver",
                            contactNumberEnc = "enc-contact",
                            zipCode = "12345",
                            baseAddress = "서울시 애착구 멍냥로 1",
                            detailAddress = null,
                            deliveryMemo = null,
                        ),
                    usedPoint = 0L,
                    totalProductAmount = 25_000L,
                    totalShippingFee = 0L,
                    idempotencyKey = "tracking-batch-${System.nanoTime()}",
                    expiresAt = now().plusMinutes(10),
                )
            em.persist(group)
            val order =
                Order.create(
                    orderGroup = group,
                    sellerId = 7_777L,
                    sellerNameSnapshot = "애착상회",
                    allocatedCouponDiscount = 0L,
                    sellerShippingFee = 0L,
                    baseShippingFeeSnapshot = null,
                    freeShippingThresholdSnapshot = null,
                    items =
                        listOf(
                            OrderItem.of(
                                productId = 1L,
                                optionCombinationId = 2L,
                                optionNameSnapshot = "기본 / 1개",
                                quantity = 1,
                                unitPriceSnapshot = 25_000L,
                                discountAllocatedAmount = 0L,
                                productVersionId = 3L,
                            ),
                        ),
                )
            em.persist(order)
            em.persist(
                Shipment.dispatch(
                    courier = em.find(CourierMaster::class.java, COURIER_CODE),
                    order = order,
                    trackingNumber = trackingNumber,
                    shipmentType = ShipmentType.INITIAL,
                    at = now().minusDays(1),
                ),
            )
            em.flush()
            em
                .createQuery("update Order o set o.status = :status where o.id = :id")
                .setParameter("status", OrderStatus.SHIPPING)
                .setParameter("id", order.id)
                .executeUpdate()
            order.publicId
        }!!

    fun shipmentOf(
        em: EntityManager,
        tx: TransactionTemplate,
        orderPublicId: String,
    ): Shipment =
        tx.execute {
            em
                .createQuery("select s from Shipment s where s.order.publicId = :publicId", Shipment::class.java)
                .setParameter("publicId", orderPublicId)
                .singleResult
        }!!
}
