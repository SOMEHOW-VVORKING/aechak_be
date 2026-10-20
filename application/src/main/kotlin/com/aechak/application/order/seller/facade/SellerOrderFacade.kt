package com.aechak.application.order.seller.facade

import com.aechak.application.file.usecase.FileUseCase
import com.aechak.application.order.seller.port.view.SellerOrderClaimView
import com.aechak.application.order.seller.port.view.SellerOrderShipmentView
import com.aechak.application.order.seller.port.view.SellerOrderSummaryView
import com.aechak.application.order.seller.service.SellerOrderService
import com.aechak.application.order.seller.usecase.SellerOrderUseCase
import com.aechak.application.order.seller.usecase.command.AcceptOrderCommand
import com.aechak.application.order.seller.usecase.command.CompleteDeliveryCommand
import com.aechak.application.order.seller.usecase.command.RegisterShipmentCommand
import com.aechak.application.order.seller.usecase.query.SellerOrderSearchQuery
import com.aechak.application.order.seller.usecase.result.RegisterShipmentResult
import com.aechak.application.order.seller.usecase.result.SellerOrderDetailResult
import com.aechak.application.order.seller.usecase.result.SellerOrderLineResult
import com.aechak.application.order.seller.usecase.result.SellerOrderSummaryResult
import com.aechak.application.seller.usecase.SellerUseCase
import com.aechak.application.support.OffsetPageResult
import com.aechak.application.user.user.usecase.UserUseCase
import com.aechak.common.error.BusinessException
import com.aechak.domain.order.error.OrderErrorCode
import com.aechak.domain.seller.seller.enums.SellerStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SellerOrderFacade(
    private val sellerOrderService: SellerOrderService,
    private val sellerUseCase: SellerUseCase,
    private val userUseCase: UserUseCase,
    private val fileUseCase: FileUseCase,
) : SellerOrderUseCase {
    @Transactional
    override fun acceptOrder(command: AcceptOrderCommand) {
        requireActiveSeller(command.sellerId)
        sellerOrderService.acceptOrder(command)
    }

    @Transactional
    override fun registerShipment(command: RegisterShipmentCommand): RegisterShipmentResult {
        requireActiveSeller(command.sellerId)
        return sellerOrderService.registerShipment(command)
    }

    @Transactional
    override fun completeDelivery(command: CompleteDeliveryCommand) {
        requireActiveSeller(command.sellerId)
        sellerOrderService.completeDelivery(command)
    }

    @Transactional(readOnly = true)
    override fun getMyOrders(query: SellerOrderSearchQuery): OffsetPageResult<SellerOrderSummaryResult> {
        requireOrderReadableSeller(query.sellerId)
        val page = sellerOrderService.getPage(query)
        val bundle = bundleOf(page.items)
        return page.map { bundle.assemble(it) }
    }

    @Transactional(readOnly = true)
    override fun getMyOrderDetail(
        sellerId: Long,
        orderPublicId: String,
    ): SellerOrderDetailResult {
        requireOrderReadableSeller(sellerId)
        val order = sellerOrderService.getOwned(sellerId, orderPublicId)
        return SellerOrderDetailResult(
            order = bundleOf(listOf(order)).assemble(order),
            items =
                sellerOrderService.getLines(listOf(order.orderId)).map {
                    SellerOrderLineResult.of(it, fileUseCase.resolveMediaUrl(it.thumbnailKey))
                },
        )
    }

    /** 배송·클레임·구매자 닉네임을 페이지 단위로 한 번에 받는다 — 주문 수만큼 쿼리가 늘지 않게. */
    private fun bundleOf(orders: List<SellerOrderSummaryView>): OrderBundle =
        OrderBundle(
            shipments = sellerOrderService.getLatestShipments(orders.map { it.orderId }),
            claims = sellerOrderService.getOpenClaims(orders.map { it.orderId }),
            nicknames = userUseCase.getAuthors(orders.map { it.buyerId }).mapValues { it.value.nickname },
            decryptPii = sellerOrderService::decryptPii,
        )

    private fun requireActiveSeller(sellerId: Long) {
        if (!sellerUseCase.isActiveSeller(sellerId)) {
            throw BusinessException(OrderErrorCode.ORDER_ACCESS_DENIED)
        }
    }

    /** 조회 자격 게이트 — 자격 소멸(탈퇴)과 제재(정지)만 막는다. 휴점·탈퇴신청 셀러는 자기 주문을 본다 */
    private fun requireOrderReadableSeller(sellerId: Long) {
        if (sellerUseCase.getSellerStatus(sellerId) !in ORDER_READABLE_SELLER_STATUSES) {
            throw BusinessException(OrderErrorCode.ORDER_ACCESS_DENIED)
        }
    }

    private class OrderBundle(
        private val shipments: Map<Long, SellerOrderShipmentView>,
        private val claims: Map<Long, SellerOrderClaimView>,
        private val nicknames: Map<Long, String>,
        private val decryptPii: (String) -> String,
    ) {
        fun assemble(view: SellerOrderSummaryView): SellerOrderSummaryResult =
            SellerOrderSummaryResult.of(
                view = view,
                buyerNickname = nicknames.getValue(view.buyerId),
                shipment = shipments[view.orderId],
                claim = claims[view.orderId],
                decryptPii = decryptPii,
            )
    }

    private companion object {
        private val ORDER_READABLE_SELLER_STATUSES =
            setOf(SellerStatus.ACTIVE, SellerStatus.PAUSED, SellerStatus.WITHDRAWAL_REQUESTED)
    }
}
