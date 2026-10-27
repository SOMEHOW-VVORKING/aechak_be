package com.aechak.application.order.claim.admin.service

import com.aechak.application.order.claim.admin.port.AdminClaimCondition
import com.aechak.application.order.claim.admin.port.AdminClaimQueryPort
import com.aechak.application.order.claim.admin.port.view.AdminClaimView
import com.aechak.application.order.claim.admin.support.AdminClaimCursorCodec
import com.aechak.application.order.claim.admin.usecase.query.AdminClaimSearchQuery
import com.aechak.application.order.claim.admin.usecase.result.AdminClaimDetailResult
import com.aechak.application.order.claim.admin.usecase.result.AdminClaimInterventionResult
import com.aechak.application.order.claim.admin.usecase.result.AdminClaimPageResult
import com.aechak.application.order.claim.admin.usecase.result.AdminClaimSummaryResult
import com.aechak.application.order.claim.port.view.SellerClaimItemView
import com.aechak.application.order.claim.service.ClaimAmountCalculator
import com.aechak.application.pii.support.PiiStringCodec
import com.aechak.application.support.CursorPageResult
import com.aechak.common.error.BusinessException
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.claim.enums.RefundStatus
import com.aechak.domain.order.error.OrderErrorCode
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.repository.OrderRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 어드민 클레임 조회. 필터·정렬·커서는 읽기 포트가, 환불 예정액은 셀러 목록과 **같은 계산기**가 맡는다 —
 * 운영자와 셀러가 같은 클레임에서 다른 환불액을 보면 그 숫자로는 아무것도 판정할 수 없다.
 */
@Service
class AdminClaimQueryService(
    private val adminClaimQueryPort: AdminClaimQueryPort,
    private val orderRepository: OrderRepository,
    private val claimAmountCalculator: ClaimAmountCalculator,
    private val piiStringCodec: PiiStringCodec,
    @param:Value("\${order.claim.return-shipping-fee:0}") private val returnShippingFee: Long,
) {
    fun searchClaims(query: AdminClaimSearchQuery): AdminClaimPageResult {
        val condition = query.toCondition()
        val fetched = adminClaimQueryPort.findPage(condition)
        val assembled = assemble(fetched)
        return AdminClaimPageResult(
            page =
                CursorPageResult.of(
                    fetched = assembled,
                    size = query.size,
                    totalCount = adminClaimQueryPort.count(query.toCountCondition()),
                    encodeCursor = { row -> AdminClaimCursorCodec.encode(condition.sort, claimIdOf(fetched, row)) },
                ),
        )
    }

    fun getClaim(claimPublicId: String): AdminClaimDetailResult {
        val view = adminClaimQueryPort.findOne(claimPublicId) ?: throw BusinessException(OrderErrorCode.CLAIM_NOT_FOUND)
        val items = adminClaimQueryPort.findItems(listOf(view.claimId))
        val orders = ordersOf(listOf(view))
        val order = orders.byId.getValue(view.orderId)
        val intervention =
            adminClaimQueryPort.findInterventions(listOf(view.claimId)).firstOrNull()?.let(AdminClaimInterventionResult::from)
        val summary = summaryOf(view, items, orders, intervention, LocalDateTime.now())
        val address = order.orderGroup.deliveryAddress
        return AdminClaimDetailResult.of(
            view = view,
            summary = summary,
            order = order,
            recipientName = piiStringCodec.decrypt(address.receiverNameEnc),
            contactNumber = piiStringCodec.decrypt(address.contactNumberEnc),
            items = items,
            intervention = intervention,
        )
    }

    private fun assemble(views: List<AdminClaimView>): List<AdminClaimSummaryResult> {
        if (views.isEmpty()) return emptyList()
        val claimIds = views.map { it.claimId }
        val itemsByClaim = adminClaimQueryPort.findItems(claimIds).groupBy { it.claimId }
        val interventionsByClaim =
            adminClaimQueryPort.findInterventions(claimIds).associate { it.claimId to AdminClaimInterventionResult.from(it) }
        val orders = ordersOf(views)
        val now = LocalDateTime.now()
        return views.map { view ->
            summaryOf(view, itemsByClaim[view.claimId].orEmpty(), orders, interventionsByClaim[view.claimId], now)
        }
    }

    private fun summaryOf(
        view: AdminClaimView,
        items: List<SellerClaimItemView>,
        orders: GroupedOrders,
        intervention: AdminClaimInterventionResult?,
        now: LocalDateTime,
    ): AdminClaimSummaryResult =
        AdminClaimSummaryResult.of(
            view = view,
            items = items,
            refundAmount = refundAmountOf(view, items, orders),
            returnShippingFee = returnShippingFeeOf(view),
            intervention = intervention,
            interventionAllowed = interventionAllowed(view),
            now = now,
        )

    /**
     * 지금 개입할 수 있는 건인지. 상태 집합은 전이표에서 뽑은 것(ClaimStatus.ADMIN_OVERRIDABLE)을,
     * 환불·교환 종결 축은 애그리거트 판정(Claim.isAdminOverridable)과 같은 조건을 쓴다 —
     * 읽기 모델에는 애그리거트가 없어 세 축을 여기서 다시 조합한다.
     *
     * 셀러 거부(REJECTED)를 뒤집을 때 대상 품목이 이미 다른 클레임에 잡혀 있으면 실행 경로가 거절하는데,
     * 그 판정은 품목 단위 질의라 목록에서는 보지 않는다 — 목록의 이 값은 "열려 있다"까지다.
     */
    private fun interventionAllowed(view: AdminClaimView): Boolean =
        view.status in ClaimStatus.ADMIN_OVERRIDABLE &&
            (view.refundStatus == null || view.refundStatus == RefundStatus.NONE) &&
            !exchangeSettled(view)

    /** Claim.isExchangeSettled와 같은 조건 — 교환은 종결돼도 상태·환불 축이 움직이지 않는다. */
    private fun exchangeSettled(view: AdminClaimView): Boolean =
        view.claimType == ClaimType.EXCHANGE && view.status == ClaimStatus.APPROVED && view.resolvedAt != null

    /** 환불 예정액 = 저장된 품목 환불액 합 + 배송비 환불액. 셀러 목록과 같은 계산 경로다. */
    private fun refundAmountOf(
        view: AdminClaimView,
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

    /** 반품 발송비는 취소에 발생하지 않는다. 셀러 조회와 같은 설정값을 본다. */
    private fun returnShippingFeeOf(view: AdminClaimView): Long? = if (view.claimType == ClaimType.CANCEL) null else returnShippingFee

    private fun ordersOf(views: List<AdminClaimView>): GroupedOrders {
        val orders = orderRepository.findAllByOrderGroupIdIn(views.map { it.orderGroupId }.distinct())
        return GroupedOrders(
            byId = orders.associateBy { it.id },
            byGroupId = orders.groupBy { it.orderGroup.id },
        )
    }

    /** 커서는 정렬 키(클레임 id)로 만든다 — 요약 결과에는 내부 id가 없어 같은 순서의 원본 행에서 되찾는다. */
    private fun claimIdOf(
        views: List<AdminClaimView>,
        row: AdminClaimSummaryResult,
    ): Long = views.first { it.claimPublicId == row.claimPublicId }.claimId

    private class GroupedOrders(
        val byId: Map<Long, Order>,
        val byGroupId: Map<Long, List<Order>>,
    )
}
