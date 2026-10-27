package com.aechak.domain.order.claim

import com.aechak.common.error.BusinessException
import com.aechak.domain.order.claim.enums.ClaimFaultDecision
import com.aechak.domain.order.claim.enums.ClaimInterventionReason
import com.aechak.domain.order.claim.enums.ClaimReasonCode
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.claim.enums.RefundStatus
import com.aechak.domain.order.claim.enums.ReturnCostBearer
import com.aechak.domain.order.claim.enums.SellerApprovalStatus
import com.aechak.domain.order.error.OrderErrorCode
import com.aechak.domain.order.group.DeliveryAddressSnapshot
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.OrderItem
import com.aechak.domain.order.order.enums.OrderStatus
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 운영자 개입 규칙. 깨지면 ① 셀러 거부가 영구 종결로 남아 분쟁이 끝나지 않거나
 * ② 환불이 나간 건이 개입으로 다시 닫히거나 ③ 뒤집은 기록이 남지 않아 판정 근거가 사라진다.
 */
class ClaimInterventionTest {
    @Test
    fun `셀러 거부를 뒤집으면 개입 완료로 종결되고 무엇을 덮었는지가 기록에 남는다`() {
        val claim = requestedClaim().apply { rejectBySeller("하자가 확인되지 않습니다") }
        val sellerResolvedAt = claim.resolvedAt

        val intervention =
            claim.overrideByAdmin(
                adminId = ADMIN_ID,
                faultDecision = ClaimFaultDecision.SELLER_FAULT,
                reason = ClaimInterventionReason.EVIDENCE_SUPPORTS_BUYER,
                reasonDetail = "사진으로 파손이 확인됨",
                returnCostBearer = null,
                internalMemo = "재발 시 셀러 경고",
            )

        assertEquals(ClaimStatus.ADMIN_OVERRIDDEN, claim.status)
        assertEquals("사진으로 파손이 확인됨", claim.adminReason)
        assertNotNull(claim.resolvedAt)

        assertEquals(ADMIN_ID, intervention.adminId)
        assertEquals(ClaimFaultDecision.SELLER_FAULT, intervention.faultDecision)
        assertEquals(ClaimInterventionReason.EVIDENCE_SUPPORTS_BUYER, intervention.reason)
        assertEquals("사진으로 파손이 확인됨", intervention.reasonDetail)
        assertEquals("재발 시 셀러 경고", intervention.internalMemo)
        assertEquals(ClaimStatus.REJECTED, intervention.previousStatus, "무엇을 덮었는지가 사라지면 기록이 쓸모없다")
        assertEquals(SellerApprovalStatus.REJECTED, intervention.previousSellerApprovalStatus)
        assertEquals(
            sellerResolvedAt,
            intervention.previousResolvedAt,
            "셀러 종결 시각이 개입 시각으로 덮이므로 기록이 들고 있어야 한다",
        )
        assertEquals("하자가 확인되지 않습니다", claim.rejectionReason, "셀러가 쓴 거부 사유는 지우지 않는다")
    }

    @Test
    fun `개입은 환불 축을 건드리지 않아 집행 대상이 되지도 집행되지도 않는다`() {
        val claim = requestedClaim().apply { rejectBySeller("거부") }

        claim.overrideByAdmin(
            adminId = ADMIN_ID,
            faultDecision = ClaimFaultDecision.SELLER_FAULT,
            reason = ClaimInterventionReason.EVIDENCE_SUPPORTS_BUYER,
            reasonDetail = null,
            returnCostBearer = null,
            internalMemo = null,
        )

        assertEquals(RefundStatus.NONE, claim.refundStatus, "개입이 환불 축을 움직이면 집행 경로가 두 벌이 된다")
        assertFalse(claim.isRefundExecutable(), "개입 종결 건이 환불 스캔에 걸리면 판정만으로 돈이 나간다")
        assertEquals(
            OrderErrorCode.INVALID_CLAIM_STATUS_TRANSITION,
            assertFailsWith<BusinessException> { claim.markRefundRequested() }.errorCode,
        )
    }

    @Test
    fun `환불이 한 번이라도 움직인 건은 개입할 수 없다`() {
        val requested = requestedClaim().apply { approveBySeller(null) }
        requested.markRefundRequested()
        val refunded = requestedClaim().apply { approveBySeller(null) }
        refunded.markRefundRequested()
        refunded.markRefunded()
        val failed = requestedClaim().apply { approveBySeller(null) }
        failed.markRefundRequested()
        failed.markRefundFailed()

        listOf(requested, refunded, failed).forEach { claim ->
            assertFalse(claim.isAdminOverridable(), "환불 상태 ${claim.refundStatus} 인 건이 개입 대상이다")
            assertEquals(
                OrderErrorCode.CLAIM_NOT_OVERRIDABLE,
                assertFailsWith<BusinessException> { intervene(claim) }.errorCode,
            )
        }
        assertEquals(RefundStatus.REQUESTED, requested.refundStatus, "거절된 개입이 상태를 바꿔선 안 된다")
        assertEquals(ClaimStatus.APPROVED, requested.status)
        assertEquals(RefundStatus.DONE, refunded.refundStatus)
        assertEquals(RefundStatus.REFUND_FAILED, failed.refundStatus)
        assertEquals(ClaimStatus.REFUND_FAILED, failed.status)
    }

    @Test
    fun `철회된 건과 이미 개입한 건은 다시 개입할 수 없다`() {
        val withdrawn = requestedClaim().apply { withdrawByBuyer() }
        val overridden = requestedClaim().apply { rejectBySeller("거부") }
        intervene(overridden)

        listOf(withdrawn, overridden).forEach { claim ->
            assertFalse(claim.isAdminOverridable())
            assertEquals(
                OrderErrorCode.CLAIM_NOT_OVERRIDABLE,
                assertFailsWith<BusinessException> { intervene(claim) }.errorCode,
            )
        }
        assertEquals(ClaimStatus.WITHDRAWN, withdrawn.status)
        assertNull(withdrawn.adminReason, "거절된 개입이 사유를 쓰면 안 된다")
    }

    @Test
    fun `요청 대기와 회수 중과 승인 건은 개입할 수 있다`() {
        assertTrue(requestedClaim().isAdminOverridable())
        assertTrue(collectionClaim(ClaimType.RETURN).apply { approveBySeller(ReturnCostBearer.BUYER) }.isAdminOverridable())
        assertTrue(requestedClaim().apply { approveBySeller(null) }.isAdminOverridable())
    }

    @Test
    fun `판정에서 고를 수 없는 사유는 거절하고 클레임을 그대로 둔다`() {
        val claim = requestedClaim().apply { rejectBySeller("거부") }

        val exception =
            assertFailsWith<BusinessException> {
                claim.overrideByAdmin(
                    adminId = ADMIN_ID,
                    faultDecision = ClaimFaultDecision.SELLER_FAULT,
                    reason = ClaimInterventionReason.BUYER_ABUSE,
                    reasonDetail = null,
                    returnCostBearer = null,
                    internalMemo = null,
                )
            }

        assertEquals(OrderErrorCode.CLAIM_INTERVENTION_REASON_NOT_SELECTABLE, exception.errorCode)
        assertEquals(ClaimStatus.REJECTED, claim.status)
        assertNull(claim.adminReason)
        assertNull(claim.returnCostBearer)
    }

    @Test
    fun `모순인 판정과 사유 조합은 모두 막혀 있다`() {
        assertFalse(ClaimInterventionReason.BUYER_ABUSE.isSelectableFor(ClaimFaultDecision.SELLER_FAULT))
        assertFalse(ClaimInterventionReason.SELLER_UNRESPONSIVE.isSelectableFor(ClaimFaultDecision.BUYER_FAULT))
        assertFalse(ClaimInterventionReason.SELLER_DECISION_UNFAIR.isSelectableFor(ClaimFaultDecision.CLAIM_DISMISSED))
        assertFalse(ClaimInterventionReason.EVIDENCE_SUPPORTS_BUYER.isSelectableFor(ClaimFaultDecision.CLAIM_DISMISSED))
        assertFalse(ClaimInterventionReason.EVIDENCE_SUPPORTS_SELLER.isSelectableFor(ClaimFaultDecision.SELLER_FAULT))

        ClaimFaultDecision.entries.forEach { decision ->
            assertTrue(
                ClaimInterventionReason.OTHER.isSelectableFor(decision),
                "$decision 에 기타가 없으면 어휘에 없는 분쟁을 기록할 수 없다",
            )
        }
    }

    @Test
    fun `기타 사유는 상세 없이 받지 않는다`() {
        val claim = requestedClaim()

        val exception =
            assertFailsWith<BusinessException> {
                claim.overrideByAdmin(
                    adminId = ADMIN_ID,
                    faultDecision = ClaimFaultDecision.CLAIM_DISMISSED,
                    reason = ClaimInterventionReason.OTHER,
                    reasonDetail = "   ",
                    returnCostBearer = null,
                    internalMemo = null,
                )
            }

        assertEquals(OrderErrorCode.CLAIM_INTERVENTION_REASON_DETAIL_REQUIRED, exception.errorCode)
        assertEquals(ClaimStatus.CLAIM_REQUESTED, claim.status)
    }

    @Test
    fun `상세를 비우면 고정 어휘 문구가 사유로 남는다`() {
        val claim = requestedClaim()

        claim.overrideByAdmin(
            adminId = ADMIN_ID,
            faultDecision = ClaimFaultDecision.SELLER_FAULT,
            reason = ClaimInterventionReason.SELLER_UNRESPONSIVE,
            reasonDetail = null,
            returnCostBearer = null,
            internalMemo = null,
        )

        assertEquals(ClaimInterventionReason.SELLER_UNRESPONSIVE.label, claim.adminReason)
    }

    @Test
    fun `반품 개입은 판정 기본값으로 부담 주체를 정하고 명시값이 그것을 이긴다`() {
        val byDefault = collectionClaim(ClaimType.RETURN)
        byDefault.overrideByAdmin(
            adminId = ADMIN_ID,
            faultDecision = ClaimFaultDecision.SELLER_FAULT,
            reason = ClaimInterventionReason.EVIDENCE_SUPPORTS_BUYER,
            reasonDetail = null,
            returnCostBearer = null,
            internalMemo = null,
        )
        assertEquals(ReturnCostBearer.SELLER, byDefault.returnCostBearer)

        val explicit = collectionClaim(ClaimType.RETURN)
        val intervention =
            explicit.overrideByAdmin(
                adminId = ADMIN_ID,
                faultDecision = ClaimFaultDecision.SELLER_FAULT,
                reason = ClaimInterventionReason.EVIDENCE_SUPPORTS_BUYER,
                reasonDetail = null,
                returnCostBearer = ReturnCostBearer.BUYER,
                internalMemo = null,
            )
        assertEquals(ReturnCostBearer.BUYER, explicit.returnCostBearer)
        assertEquals(ReturnCostBearer.BUYER, intervention.returnCostBearer, "기록은 클레임에 실제로 적용된 값을 담아야 한다")
    }

    @Test
    fun `클레임 불가 판정은 부담 주체를 명시해도 남기지 않는다`() {
        val claim = collectionClaim(ClaimType.RETURN)

        val intervention =
            claim.overrideByAdmin(
                adminId = ADMIN_ID,
                faultDecision = ClaimFaultDecision.CLAIM_DISMISSED,
                reason = ClaimInterventionReason.BUYER_ABUSE,
                reasonDetail = null,
                // 명시값을 준다 — 생략한 입력만 보면 판정 기본값이 null인 덕에 통과하고, 덮어쓰기 구멍은 지나간다
                returnCostBearer = ReturnCostBearer.BUYER,
                internalMemo = null,
            )

        assertNull(claim.returnCostBearer, "성립하지 않은 클레임에 반품비 부담 주체가 생기면 정산이 없는 반품비를 청구한다")
        assertNull(intervention.returnCostBearer, "기록이 claimAccepted=false와 부담 주체를 함께 들면 판정이 모순된다")
        assertFalse(intervention.faultDecision.claimAccepted)
    }

    @Test
    fun `재발송으로 종결된 교환은 개입할 수 없다`() {
        val settled = settledExchangeClaim()

        assertFalse(
            settled.isAdminOverridable(),
            "교환은 환불이 없어 종결돼도 상태가 APPROVED에 머문다 — 상태·환불 두 축만 보면 끝난 건이 작업 목록에 남는다",
        )
        assertTrue(settled.isExchangeSettled())
        val resolvedAt = settled.resolvedAt

        assertEquals(OrderErrorCode.CLAIM_NOT_OVERRIDABLE, assertFailsWith<BusinessException> { intervene(settled) }.errorCode)
        assertEquals(ClaimStatus.APPROVED, settled.status)
        assertEquals(resolvedAt, settled.resolvedAt, "개입이 종결 시각을 덮으면 재발송이 언제 끝났는지가 사라진다")
    }

    @Test
    fun `재발송 전 교환은 개입할 수 있다`() {
        val collected = collectionClaim(ClaimType.EXCHANGE).apply { approveBySeller(ReturnCostBearer.SELLER) }
        collected.startCollection()
        collected.completeCollection()

        assertFalse(collected.isExchangeSettled(), "회수만 끝난 교환은 아직 재발송이 남아 열린 분쟁이다")
        assertTrue(collected.isAdminOverridable())

        val intervention = intervene(collected)

        assertEquals(ClaimStatus.ADMIN_OVERRIDDEN, collected.status)
        assertEquals(ClaimStatus.APPROVED, intervention.previousStatus)
    }

    @Test
    fun `취소 클레임은 부담 주체를 명시해도 비워 둔다`() {
        val claim = requestedClaim()

        val intervention =
            claim.overrideByAdmin(
                adminId = ADMIN_ID,
                faultDecision = ClaimFaultDecision.BUYER_FAULT,
                reason = ClaimInterventionReason.BUYER_CHANGE_OF_MIND,
                reasonDetail = null,
                returnCostBearer = ReturnCostBearer.BUYER,
                internalMemo = null,
            )

        assertNull(claim.returnCostBearer, "발송 전 취소에는 반품 발송비가 없다 — 셀러 승인 경로와 같은 규칙")
        assertNull(intervention.returnCostBearer)
        assertEquals(ClaimFaultDecision.BUYER_FAULT, intervention.faultDecision, "귀책 판정 자체는 그대로 남는다")
    }

    @Test
    fun `공백만 있는 상세와 메모는 비어 있는 것으로 본다`() {
        val claim = requestedClaim()

        val intervention =
            claim.overrideByAdmin(
                adminId = ADMIN_ID,
                faultDecision = ClaimFaultDecision.SELLER_FAULT,
                reason = ClaimInterventionReason.SELLER_UNRESPONSIVE,
                reasonDetail = "  ",
                returnCostBearer = null,
                internalMemo = "  ",
            )

        assertNull(intervention.reasonDetail)
        assertNull(intervention.internalMemo)
        assertEquals(ClaimInterventionReason.SELLER_UNRESPONSIVE.label, claim.adminReason)
    }

    private fun intervene(claim: Claim): ClaimIntervention =
        claim.overrideByAdmin(
            adminId = ADMIN_ID,
            faultDecision = ClaimFaultDecision.SELLER_FAULT,
            reason = ClaimInterventionReason.SELLER_UNRESPONSIVE,
            reasonDetail = null,
            returnCostBearer = null,
            internalMemo = null,
        )

    /** 재발송까지 끝난 교환 — 상태는 APPROVED, 환불 축은 NONE에 그대로 머물고 종결 시각만 찍힌다. */
    private fun settledExchangeClaim(): Claim {
        val claim = collectionClaim(ClaimType.EXCHANGE).apply { approveBySeller(ReturnCostBearer.SELLER) }
        claim.startCollection()
        claim.completeCollection()
        claim.completeExchange()
        return claim
    }

    private fun collectionClaim(claimType: ClaimType): Claim {
        val order = order(OrderStatus.DELIVERED)
        return persisted(
            Claim.request(
                order = order,
                claimType = claimType,
                reasonCode = ClaimReasonCode.DEFECTIVE,
                reasonDetail = null,
                items = listOf(ClaimItem.of(order.items.first(), quantity = 1, refundAmount = 10_000L)),
                autoApproveDueAt = DUE_AT,
            ),
        )
    }

    private fun requestedClaim(): Claim {
        val order = order()
        return persisted(
            Claim.request(
                order = order,
                claimType = ClaimType.CANCEL,
                reasonCode = ClaimReasonCode.CHANGE_OF_MIND,
                reasonDetail = null,
                items = listOf(ClaimItem.of(order.items.first(), quantity = 1, refundAmount = 10_000L)),
                autoApproveDueAt = DUE_AT,
            ),
        )
    }

    /** 개입 기록은 claim_id를 담아야 해 저장된 클레임만 받는다 — 식별자를 심어 그 전제를 만든다. */
    private fun persisted(claim: Claim): Claim =
        claim.apply {
            Claim::class.java
                .getDeclaredField("id")
                .apply { isAccessible = true }
                .setLong(this, 9001L)
        }

    private fun order(status: OrderStatus = OrderStatus.PAID): Order =
        Order
            .create(
                orderGroup = orderGroup(),
                sellerId = 77L,
                sellerNameSnapshot = "멍멍상회",
                allocatedCouponDiscount = 0L,
                sellerShippingFee = 3_000L,
                baseShippingFeeSnapshot = 3_000L,
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
            ).apply {
                Order::class.java
                    .getDeclaredField("id")
                    .apply { isAccessible = true }
                    .setLong(this, 4242L)
                Order::class.java
                    .getDeclaredField("status")
                    .apply { isAccessible = true }
                    .set(this, status)
            }

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
            totalShippingFee = 3_000L,
            idempotencyKey = "claim-intervention-1",
            expiresAt = LocalDateTime.of(2026, 10, 18, 12, 0, 0),
        )

    private companion object {
        private const val ADMIN_ID = 501L
        private val DUE_AT = LocalDateTime.of(2026, 10, 20, 14, 0, 0)
    }
}
