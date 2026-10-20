package com.aechak.domain.order.shipment

import com.aechak.domain.order.group.DeliveryAddressSnapshot
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.OrderItem
import com.aechak.domain.order.shipment.enums.ShipmentType
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 추적 상태 반영 계약. 택배사 문구는 길이도 공백도 보장되지 않는 자유 문자열이고,
 * 이 메서드가 배송완료 전이까지 일으키면 추적 한 번이 구매확정 기한을 열어 버린다 —
 * 그래서 담는 값과 건드리지 않는 값이 둘 다 계약이다.
 */
class ShipmentTrackingSyncTest {
    @Test
    fun `택배사가 준 상태와 조회 시각을 담는다`() {
        val shipment = shipment()
        val syncedAt = LocalDateTime.of(2026, 10, 20, 9, 30)

        shipment.syncTracking("배송중", syncedAt)

        assertEquals("배송중", shipment.lastTrackingStatus)
        assertEquals(syncedAt, shipment.lastSyncedAt)
    }

    @Test
    fun `컬럼 길이를 넘는 문구는 잘라서 담는다`() {
        val shipment = shipment()

        shipment.syncTracking("가".repeat(Shipment.MAX_LAST_TRACKING_STATUS_LENGTH + 10))

        assertEquals(
            Shipment.MAX_LAST_TRACKING_STATUS_LENGTH,
            shipment.lastTrackingStatus?.length,
            "길이를 넘긴 문구를 그대로 담으면 저장에서 깨진다",
        )
    }

    @Test
    fun `앞뒤 공백은 떼고 담는다`() {
        val shipment = shipment()

        shipment.syncTracking("  배달완료\n")

        assertEquals("배달완료", shipment.lastTrackingStatus)
    }

    @Test
    fun `다시 조회하면 최신 상태로 덮어쓴다`() {
        val shipment = shipment()
        val secondSyncedAt = LocalDateTime.of(2026, 10, 20, 18, 0)

        shipment.syncTracking("집화완료", LocalDateTime.of(2026, 10, 20, 9, 0))
        shipment.syncTracking("배달완료", secondSyncedAt)

        assertEquals("배달완료", shipment.lastTrackingStatus)
        assertEquals(secondSyncedAt, shipment.lastSyncedAt)
    }

    @Test
    fun `추적 반영은 배송완료 시각을 건드리지 않는다`() {
        val shipment = shipment()

        shipment.syncTracking("배달완료")

        assertNull(
            shipment.deliveredAt,
            "추적 문구만으로 배송완료가 되면 구매확정 기한이 택배사 문구 한 줄로 열린다",
        )
    }

    private fun shipment(): Shipment =
        Shipment.dispatch(
            courier = CourierMaster.register(courierCode = "CJ", courierName = "CJ대한통운"),
            order = order(),
            trackingNumber = "123456789012",
            shipmentType = ShipmentType.INITIAL,
        )

    private fun order(): Order =
        Order.create(
            orderGroup = orderGroup(),
            sellerId = 77L,
            sellerNameSnapshot = "멍멍상회",
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
                        unitPriceSnapshot = 10_000L,
                        discountAllocatedAmount = 0L,
                        productVersionId = 3L,
                    ),
                ),
        )

    private fun orderGroup(): OrderGroup =
        OrderGroup.create(
            buyerId = 1L,
            deliveryAddressId = 100L,
            deliveryAddress =
                DeliveryAddressSnapshot(
                    receiverNameEnc = "enc-receiver",
                    contactNumberEnc = "enc-contact",
                    zipCode = "06236",
                    baseAddress = "서울특별시 강남구 테헤란로 1",
                    detailAddress = null,
                    deliveryMemo = null,
                ),
            usedPoint = 0L,
            totalProductAmount = 10_000L,
            totalShippingFee = 0L,
            idempotencyKey = "shipment-tracking-sync-test",
            expiresAt = LocalDateTime.of(2026, 10, 20, 12, 0),
        )
}
