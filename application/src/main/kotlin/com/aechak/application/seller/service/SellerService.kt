package com.aechak.application.seller.service

import com.aechak.common.error.BusinessException
import com.aechak.domain.seller.error.SellerErrorCode
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

    /** 셀러 1건 — 셀러가 아니면 null. */
    fun find(userId: Long): Seller? = sellerRepository.findByUserId(userId)

    /** 셀러 1건 + 활성 게이트. 셀러가 아니면 10000, 활성이 아니면 10002. 셀러센터 쓰기 경로의 공용 입구. */
    fun getActive(userId: Long): Seller {
        val seller = find(userId) ?: throw BusinessException(SellerErrorCode.SELLER_NOT_FOUND)
        if (seller.status != SellerStatus.ACTIVE) {
            throw BusinessException(SellerErrorCode.SELLER_NOT_ACTIVE)
        }
        return seller
    }

    /** 입점 승인 부수효과 — ACTIVE 셀러 개점. 초기 배송비 0원은 승인 직후 스토어 설정 화면에서 조정을 유도한다. */
    fun open(
        userId: Long,
        storeName: String,
        baseShippingFee: Long,
    ): Seller = sellerRepository.save(Seller.open(userId, storeName, baseShippingFee))
}
