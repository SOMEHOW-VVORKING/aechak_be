package com.aechak.application.seller.service

import com.aechak.application.seller.usecase.command.UpdateShippingPolicyCommand
import com.aechak.domain.seller.seller.Seller
import org.springframework.stereotype.Service

/** 배송 정책 저장 로직 보관함 — Facade에서만 호출된다. 셀러 로딩·활성 게이트는 SellerService 소관. */
@Service
class SellerShippingPolicyService {
    fun applyShippingPolicy(
        seller: Seller,
        command: UpdateShippingPolicyCommand,
    ) {
        seller.updateShippingPolicy(
            defaultCourierCode = command.defaultCourierCode,
            baseShippingFee = command.baseShippingFee,
            freeShippingThreshold = command.freeShippingThreshold,
            originZipCode = command.originZipCode,
            originRoadAddress = command.originRoadAddress,
            originDetailAddress = command.originDetailAddress,
            returnSameAsOrigin = command.returnSameAsOrigin,
            returnZipCode = command.returnZipCode,
            returnRoadAddress = command.returnRoadAddress,
            returnDetailAddress = command.returnDetailAddress,
            jejuExtraFee = command.jejuExtraFee,
            remoteAreaExtraFee = command.remoteAreaExtraFee,
            returnFee = command.returnFee,
            exchangeFee = command.exchangeFee,
            avgDispatchDays = command.avgDispatchDays,
            shippingPolicyText = command.shippingPolicyText,
        )
    }
}
