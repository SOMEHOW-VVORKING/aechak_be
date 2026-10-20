package com.aechak.application.order.claim.service

import com.aechak.application.order.claim.port.SellerClaimCondition
import com.aechak.application.order.claim.port.SellerClaimQueryPort
import com.aechak.application.order.claim.port.view.SellerClaimItemView
import com.aechak.application.order.claim.port.view.SellerClaimView
import com.aechak.application.order.claim.usecase.query.SellerClaimSearchQuery
import com.aechak.application.order.claim.usecase.result.SellerClaimDetailResult
import com.aechak.application.order.claim.usecase.result.SellerClaimPageResult
import com.aechak.application.order.claim.usecase.result.SellerClaimSummaryResult
import com.aechak.application.pii.support.PiiStringCodec
import com.aechak.application.support.OffsetPageResult
import com.aechak.common.error.BusinessException
import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.error.OrderErrorCode
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.repository.OrderRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * 셀러 클레임 조회. 필터·정렬·페이징·건수는 읽기 포트가, 환불 예정액은 요청 시점 계산기가 맡는다 —
 * 구매자 목록과 같은 함수를 써야 같은 클레임의 환불액이 두 화면에서 갈리지 않는다.
 */
@Service
class SellerClaimQueryService(
    private val sellerClaimQueryPort: SellerClaimQueryPort,
    private val orderRepository: OrderRepository,
    private val claimAmountCalculator: ClaimAmountCalculator,
    private val piiStringCodec: PiiStringCodec,
    @param:Value("\${order.claim.return-shipping-fee:0}") private val returnShippingFee: Long,
) {
    fun searchClaims(query: SellerClaimSearchQuery): SellerClaimPageResult {
        val condition = query.toCondition()
        val views = sellerClaimQueryPort.findPage(condition)
        val assembled = assemble(views)
        return SellerClaimPageResult.of(
            page =
                OffsetPageResult(
                    items = assembled,
                    totalCount = sellerClaimQueryPort.count(condition),
                    page = query.page,
                    size = query.size,
                ),
            counts = countsOf(condition),
        )
    }

    fun getClaim(
        claimPublicId: String,
        sellerId: Long,
    ): SellerClaimDetailResult {
        val view =
            sellerClaimQueryPort.findOne(claimPublicId, sellerId)
                ?: throw BusinessException(OrderErrorCode.CLAIM_NOT_FOUND)
        val items = sellerClaimQueryPort.findItems(listOf(view.claimId))
        val orders = ordersOf(listOf(view))
        val order = orders.byId.getValue(view.orderId)
        val summary =
            SellerClaimSummaryResult.of(
                view = view,
                items = items,
                refundAmount = refundAmountOf(view, items, orders),
                returnShippingFee = returnShippingFeeOf(view),
                dispatched = sellerClaimQueryPort.findDispatchedOrderIds(listOf(view.orderId)).isNotEmpty(),
            )
        val address = order.orderGroup.deliveryAddress
        return SellerClaimDetailResult.of(
            view = view,
            summary = summary,
            order = order,
            recipientName = piiStringCodec.decrypt(address.receiverNameEnc),
            contactNumber = piiStringCodec.decrypt(address.contactNumberEnc),
            items = items,
        )
    }

    private fun assemble(views: List<SellerClaimView>): List<SellerClaimSummaryResult> {
        if (views.isEmpty()) return emptyList()
        val itemsByClaim = sellerClaimQueryPort.findItems(views.map { it.claimId }).groupBy { it.claimId }
        val dispatchedOrderIds = sellerClaimQueryPort.findDispatchedOrderIds(views.map { it.orderId }.distinct()).toSet()
        val orders = ordersOf(views)
        return views.map { view ->
            val items = itemsByClaim[view.claimId].orEmpty()
            SellerClaimSummaryResult.of(
                view = view,
                items = items,
                refundAmount = refundAmountOf(view, items, orders),
                returnShippingFee = returnShippingFeeOf(view),
                dispatched = view.orderId in dispatchedOrderIds,
            )
        }
    }

    private fun countsOf(condition: SellerClaimCondition): Map<ClaimType, Long> =
        sellerClaimQueryPort.countByClaimType(condition).associate { it.claimType to it.count }

    /**
     * 환불 예정액 = 저장된 품목 환불액 합 + 배송비 환불액. 배송비 환불 판정과 적립금 안분은
     * 요청 시점과 같은 계산기를 거친다 — 입력(단가·수량·할인·배송비·사용 적립금)이 전부 불변이라 값이 같다.
     */
    private fun refundAmountOf(
        view: SellerClaimView,
        items: List<SellerClaimItemView>,
        orders: GroupedOrders,
    ): Long {
        val order = orders.byId.getValue(view.orderId)
        val itemTotal = items.fold(0L) { sum, item -> Math.addExact(sum, item.refundAmount) }
        val shippingRefund =
            claimAmountCalculator.shippingFeeRefund(
                order = order,
                groupOrders = orders.byGroupId.getValue(view.orderGroupId),
                usedPoint = order.orderGroup.usedPoint,
                targets = items.associate { it.orderItemId to it.quantity },
            )
        return Math.addExact(itemTotal, shippingRefund)
    }

    /** 반품 발송비는 취소에 발생하지 않는다. 셀러 배송 정책 테이블이 생기기 전까지는 설정값이 정책 스냅샷이다. */
    private fun returnShippingFeeOf(view: SellerClaimView): Long? = if (view.claimType == ClaimType.CANCEL) null else returnShippingFee

    /** 적립금 안분 분모가 같은 그룹의 주문 전체라 그룹 단위로 묶어 한 번에 읽는다. */
    private fun ordersOf(views: List<SellerClaimView>): GroupedOrders {
        val orders = orderRepository.findAllByOrderGroupIdIn(views.map { it.orderGroupId }.distinct())
        return GroupedOrders(
            byId = orders.associateBy { it.id },
            byGroupId = orders.groupBy { it.orderGroup.id },
        )
    }

    private class GroupedOrders(
        val byId: Map<Long, Order>,
        val byGroupId: Map<Long, List<Order>>,
    )
}
