package com.aechak.application.seller.facade

import com.aechak.application.seller.service.SellerService
import com.aechak.application.seller.usecase.SellerUseCase
import com.aechak.domain.seller.seller.enums.SellerStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * SellerUseCase의 유일한 구현체. @Transactional 경계는 여기 고정.
 * 규칙은 user 도메인 템플릿(UserFacade) 참조.
 */
@Service
class SellerFacade(
    private val sellerService: SellerService,
) : SellerUseCase {
    @Transactional(readOnly = true)
    override fun requireSellerStatus(userId: Long): SellerStatus = sellerService.requireStatus(userId)

    @Transactional(readOnly = true)
    override fun findSellerStatus(userId: Long): SellerStatus? = sellerService.find(userId)?.status
}
