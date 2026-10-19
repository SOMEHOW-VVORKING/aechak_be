package com.aechak.application.seller.facade

import com.aechak.application.order.shipment.usecase.CourierUseCase
import com.aechak.application.seller.service.SellerShippingPolicyService
import com.aechak.application.seller.usecase.SellerShippingPolicyUseCase
import com.aechak.application.seller.usecase.command.UpdateShippingPolicyCommand
import com.aechak.application.seller.usecase.result.ShippingPolicyResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** SellerShippingPolicyUseCase의 유일한 구현체. @Transactional 경계는 여기 고정. */
@Service
class SellerShippingPolicyFacade(
    private val sellerShippingPolicyService: SellerShippingPolicyService,
    private val courierUseCase: CourierUseCase,
) : SellerShippingPolicyUseCase {
    @Transactional(readOnly = true)
    override fun getShippingPolicy(sellerId: Long): ShippingPolicyResult =
        ShippingPolicyResult.from(sellerShippingPolicyService.getActiveSeller(sellerId))

    @Transactional
    override fun updateShippingPolicy(command: UpdateShippingPolicyCommand) {
        // 셀러 게이트가 택배사 검증보다 앞선다 — 비셀러에게 택배사 마스터 존재 여부를 알려주지 않는다
        val seller = sellerShippingPolicyService.getActiveSeller(command.sellerId)
        command.defaultCourierCode?.let { courierUseCase.verifyActiveCourier(it) }
        sellerShippingPolicyService.applyShippingPolicy(seller, command)
    }
}
