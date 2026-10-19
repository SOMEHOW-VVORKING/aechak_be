package com.aechak.application.seller.service

import com.aechak.application.seller.usecase.command.UpdateStoreCommand
import com.aechak.domain.seller.application.enums.ApplicationStatus
import com.aechak.domain.seller.application.repository.SellerApplicationRepository
import com.aechak.domain.seller.seller.Seller
import org.springframework.stereotype.Service

/** 스토어 설정 조회·저장 로직 보관함 — Facade에서만 호출된다. 셀러 로딩·활성 게이트는 SellerService 소관. */
@Service
class SellerStoreService(
    private val sellerApplicationRepository: SellerApplicationRepository,
) {
    /** 신청 상태 — 신청 이력이 없으면 null. 셀러가 아닌 계정의 화면 분기용. */
    fun findApplicationStatus(userId: Long): ApplicationStatus? = sellerApplicationRepository.findStatusByUserId(userId)

    fun applyStore(
        seller: Seller,
        command: UpdateStoreCommand,
    ) {
        seller.updateStore(
            storeName = command.storeName,
            brandIntro = command.brandIntro,
            profileImageKey = command.profileImageKey,
            bannerImageKey = command.bannerImageKey,
            zipCode = command.zipCode,
            roadAddress = command.roadAddress,
            detailAddress = command.detailAddress,
        )
    }
}
