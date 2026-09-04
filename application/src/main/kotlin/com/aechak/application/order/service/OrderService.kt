package com.aechak.application.order.service

import com.aechak.application.order.port.OrderCatalogQueryPort
import com.aechak.application.order.port.view.OrderCatalogItemView
import com.aechak.application.order.usecase.command.CreateOrderGroupCommand
import com.aechak.application.order.usecase.result.CreateOrderGroupResult
import com.aechak.application.pii.support.PiiStringCodec
import com.aechak.application.user.address.usecase.result.DeliveryAddressResult
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.order.cart.CartItem
import com.aechak.domain.order.error.OrderErrorCode
import com.aechak.domain.order.group.DeliveryAddressSnapshot
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.group.repository.OrderGroupRepository
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.OrderItem
import com.aechak.domain.order.order.repository.OrderRepository
import com.aechak.domain.product.option.repository.OptionCombinationRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * order 도메인 비즈니스 로직 보관함 — Facade에서만 호출된다.
 * 금액 계산과 셀러 분할은 여기가 구속력 있는 계산이고, FE가 보낸 금액은 대조에만 쓴다.
 */
@Service
class OrderService(
    private val orderGroupRepository: OrderGroupRepository,
    private val orderRepository: OrderRepository,
    private val optionCombinationRepository: OptionCombinationRepository,
    private val orderCatalogQueryPort: OrderCatalogQueryPort,
    private val piiStringCodec: PiiStringCodec,
) {
    /** 멱등 재요청이면 최초 생성 결과. 남의 키면 응답 유출을 막으려 거부한다. */
    fun findByIdempotencyKey(
        idempotencyKey: String,
        buyerId: Long,
    ): CreateOrderGroupResult? {
        val existing = orderGroupRepository.findByIdempotencyKey(idempotencyKey) ?: return null
        if (existing.buyerId != buyerId) {
            throw BusinessException(CommonErrorCode.IDEMPOTENCY_KEY_ACCESS_DENIED)
        }
        return CreateOrderGroupResult.from(existing)
    }

    fun createOrderGroup(
        command: CreateOrderGroupCommand,
        cartItems: List<CartItem>,
        address: DeliveryAddressResult,
    ): CreateOrderGroupResult {
        val now = LocalDateTime.now()
        val selected = selectCartItems(cartItems, command.cartItemIds)
        val catalog = loadOrderableCatalog(selected)
        val pricing = priceOrder(selected, catalog, now)

        // FE가 주문서에 표시한 금액과 대조 — 어긋나면 그 사이 가격 정책이 바뀐 것이라 생성하지 않는다
        if (pricing.finalPaymentAmount(command.usedPoint) != command.expectedFinalAmount) {
            throw BusinessException(OrderErrorCode.ORDER_AMOUNT_CHANGED)
        }
        deductStock(selected)

        val orderGroup =
            orderGroupRepository.save(
                OrderGroup.create(
                    buyerId = command.buyerId,
                    deliveryAddressId = address.addressId,
                    deliveryAddress = snapshotOf(address),
                    usedPoint = command.usedPoint,
                    totalProductAmount = pricing.totalProductAmount,
                    totalShippingFee = pricing.totalShippingFee,
                    idempotencyKey = command.idempotencyKey,
                    expiresAt = now.plusMinutes(PAYMENT_WINDOW_MINUTES),
                ),
            )
        orderRepository.saveAll(createOrders(orderGroup, pricing))
        return CreateOrderGroupResult.from(orderGroup)
    }

    /** 라인 단가 확정(할인·추가금 포함)과 셀러별 배송비 판정 */
    private fun priceOrder(
        selected: List<CartItem>,
        catalog: Map<Long, OrderCatalogItemView>,
        now: LocalDateTime,
    ): OrderPricing {
        val lines =
            selected.map { item ->
                val view = catalog.getValue(item.optionCombinationId)
                OrderLine(view, item.quantity, view.unitPriceAt(now))
            }
        val linesBySeller = lines.groupBy { it.view.sellerId }
        return OrderPricing(
            linesBySeller = linesBySeller,
            shippingFees = linesBySeller.mapValues { (_, sellerLines) -> shippingFee(sellerLines) },
        )
    }

    /** 최종 재고 판정은 조건부 원자 UPDATE. id 오름차순으로 깎아 교차 주문 간 데드락을 예방한다 */
    private fun deductStock(selected: List<CartItem>) {
        selected.sortedBy { it.optionCombinationId }.forEach { item ->
            if (!optionCombinationRepository.deductStock(item.optionCombinationId, item.quantity)) {
                throw BusinessException(OrderErrorCode.INSUFFICIENT_STOCK)
            }
        }
    }

    private fun createOrders(
        orderGroup: OrderGroup,
        pricing: OrderPricing,
    ): List<Order> =
        pricing.linesBySeller.map { (sellerId, sellerLines) ->
            Order.create(
                orderGroup = orderGroup,
                sellerId = sellerId,
                sellerNameSnapshot = sellerLines.first().view.storeName,
                allocatedCouponDiscount = 0,
                sellerShippingFee = pricing.shippingFees.getValue(sellerId),
                items =
                    sellerLines.map { line ->
                        OrderItem.of(
                            productId = line.view.productId,
                            optionCombinationId = line.view.optionCombinationId,
                            quantity = line.quantity,
                            unitPriceSnapshot = line.unitPrice,
                            discountAllocatedAmount = 0,
                            productVersionId =
                                requireNotNull(line.view.latestProductVersionId) {
                                    "주문 품목에는 상품 버전이 필수입니다 (optionCombinationId=${line.view.optionCombinationId})"
                                },
                        )
                    },
            )
        }

    /** 선택 항목이 전부 본인 장바구니에 있어야 한다. 중복 id는 하나로 접는다 */
    private fun selectCartItems(
        cartItems: List<CartItem>,
        cartItemIds: List<Long>,
    ): List<CartItem> {
        val byId = cartItems.associateBy { it.id }
        return cartItemIds.distinct().map { id ->
            byId[id] ?: throw BusinessException(OrderErrorCode.CART_ITEM_NOT_FOUND)
        }
    }

    private fun loadOrderableCatalog(selected: List<CartItem>): Map<Long, OrderCatalogItemView> {
        val views =
            orderCatalogQueryPort
                .findItems(selected.map { it.optionCombinationId })
                .associateBy { it.optionCombinationId }
        selected.forEach { item ->
            val view =
                views[item.optionCombinationId]
                    ?: throw BusinessException(OrderErrorCode.CART_ITEM_OPTION_NOT_FOUND)
            if (!view.orderable(item.quantity)) {
                throw BusinessException(OrderErrorCode.ORDER_ITEM_NOT_ORDERABLE)
            }
            if (view.latestProductVersionId == null) {
                throw BusinessException(OrderErrorCode.ORDER_PRODUCT_VERSION_NOT_READY)
            }
        }
        return views
    }

    /** 셀러 상품금액이 무료배송 임계 이상이면 0원. 임계가 없으면 항상 기본 배송비 */
    private fun shippingFee(sellerLines: List<OrderLine>): Long {
        val productAmount = sellerLines.sumOf { it.lineAmount }
        val threshold = sellerLines.first().view.freeShippingThreshold
        return if (threshold != null && productAmount >= threshold) 0 else sellerLines.first().view.baseShippingFee
    }

    /** 수령인명과 연락처는 여기서 처음 암호화됨(AES 후 Base64) — 스냅샷 컬럼 계약 */
    private fun snapshotOf(address: DeliveryAddressResult): DeliveryAddressSnapshot =
        DeliveryAddressSnapshot(
            receiverNameEnc = piiStringCodec.encrypt(address.receiverName),
            contactNumberEnc = piiStringCodec.encrypt(address.contactNumber),
            zipCode = address.zipCode,
            baseAddress = address.baseAddress,
            detailAddress = address.detailAddress,
            deliveryMemo = address.deliveryMemo,
        )

    private data class OrderLine(
        val view: OrderCatalogItemView,
        val quantity: Int,
        val unitPrice: Long,
    ) {
        val lineAmount: Long get() = unitPrice * quantity
    }

    /** 구속력 있는 서버 계산의 결과 — 셀러별 라인·배송비와 그 합계 */
    private class OrderPricing(
        val linesBySeller: Map<Long, List<OrderLine>>,
        val shippingFees: Map<Long, Long>,
    ) {
        val totalProductAmount = linesBySeller.values.flatten().sumOf { it.lineAmount }
        val totalShippingFee = shippingFees.values.sum()

        fun finalPaymentAmount(usedPoint: Long): Long = totalProductAmount + totalShippingFee - usedPoint
    }

    companion object {
        const val PAYMENT_WINDOW_MINUTES = 10L
    }
}
