package com.aechak.application.seller.service

import com.aechak.domain.seller.seller.Seller
import com.aechak.domain.seller.seller.enums.SellerStatus
import com.aechak.domain.seller.seller.repository.SellerRepository
import org.springframework.stereotype.Service

/** seller 도메인 비즈니스 로직 보관함 — Facade에서만 호출된다. */
@Service
class SellerService(
    private val sellerRepository: SellerRepository,
) {
    fun isActive(userId: Long): Boolean = sellerRepository.existsActiveByUserId(userId)

    fun getStatus(userId: Long): SellerStatus? = sellerRepository.findStatusByUserId(userId)

    /** 입점 승인 부수효과 — ACTIVE 셀러 개점. 초기 배송비 0원은 승인 직후 스토어 설정 화면에서 조정을 유도한다. */
    fun open(
        userId: Long,
        storeName: String,
        baseShippingFee: Long,
    ): Seller = sellerRepository.save(Seller.open(userId, storeName, baseShippingFee))
}
