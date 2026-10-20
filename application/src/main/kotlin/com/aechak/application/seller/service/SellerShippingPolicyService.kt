package com.aechak.application.seller.service

import com.aechak.application.seller.usecase.command.UpdateShippingPolicyCommand
import com.aechak.common.error.BusinessException
import com.aechak.domain.seller.error.SellerErrorCode
import com.aechak.domain.seller.seller.Seller
import com.aechak.domain.seller.seller.enums.SellerStatus
import com.aechak.domain.seller.seller.repository.SellerRepository
import org.springframework.stereotype.Service

/** 배송 정책 조회·저장 로직 보관함 — Facade에서만 호출된다. */
@Service
class SellerShippingPolicyService(
    private val sellerRepository: SellerRepository,
) {
    /** 셀러 1건 로딩 + 활성 게이트. 셀러가 아니면 10000, 활성이 아니면 10002. */
    fun getActiveSeller(sellerId: Long): Seller {
        val seller =
            sellerRepository.findByUserId(sellerId)
                ?: throw BusinessException(SellerErrorCode.SELLER_NOT_FOUND)
        if (seller.status != SellerStatus.ACTIVE) {
            throw BusinessException(SellerErrorCode.SELLER_NOT_ACTIVE)
        }
        return seller
    }

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
