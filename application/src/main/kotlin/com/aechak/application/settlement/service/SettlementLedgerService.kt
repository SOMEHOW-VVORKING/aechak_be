package com.aechak.application.settlement.service

import com.aechak.application.settlement.usecase.command.CreateSettlementLedgerCommand
import com.aechak.common.error.BusinessException
import com.aechak.domain.settlement.error.SettlementErrorCode
import com.aechak.domain.settlement.feepolicy.repository.SettlementFeePolicyRepository
import com.aechak.domain.settlement.settlement.Settlement
import com.aechak.domain.settlement.settlement.policy.SettlementCalendar
import com.aechak.domain.settlement.settlement.policy.ShippingFeeAllocationPolicy
import com.aechak.domain.settlement.settlement.repository.SettlementRepository
import org.springframework.stereotype.Service

/** 정산 원장 생성 로직 보관함 — Facade에서만 호출된다. */
@Service
class SettlementLedgerService(
    private val settlementRepository: SettlementRepository,
    private val settlementFeePolicyRepository: SettlementFeePolicyRepository,
) {
    /**
     * 구매확정된 주문의 품목마다 원장 1행을 만든다.
     * 멱등키로 이미 있는 품목을 먼저 걸러내 — 수동·자동 확정이 같은 주문을 다시 태워도 행이 늘지 않는다.
     * 배송비 안분은 걸러내기 전의 전체 품목으로 계산한다 — 일부만 남은 재실행에서도 품목별 배송비가 처음과 같아야 한다.
     */
    fun createOnPurchaseConfirm(command: CreateSettlementLedgerCommand): List<Settlement> {
        if (command.items.isEmpty()) {
            return emptyList()
        }
        val existingKeys =
            settlementRepository
                .findAllByIdempotencyKeyIn(command.items.map { Settlement.idempotencyKeyOf(it.orderItemId) })
                .map { it.idempotencyKey }
                .toSet()
        val missing = command.items.filter { Settlement.idempotencyKeyOf(it.orderItemId) !in existingKeys }
        if (missing.isEmpty()) {
            return emptyList()
        }

        val confirmedOn = SettlementCalendar.settlementDateOf(command.confirmedAt)
        val feePolicy =
            settlementFeePolicyRepository.findEffective(command.sellerId, confirmedOn)
                ?: throw BusinessException(SettlementErrorCode.SETTLEMENT_FEE_POLICY_NOT_FOUND)
        val shippingFees =
            ShippingFeeAllocationPolicy.allocate(command.sellerShippingFee, command.items.map { it.orderItemId })

        return settlementRepository.saveAll(
            missing.map { command.toEntity(it, shippingFees.getValue(it.orderItemId), feePolicy, confirmedOn) },
        )
    }
}
