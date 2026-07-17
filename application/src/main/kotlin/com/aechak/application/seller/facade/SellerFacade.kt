package com.aechak.application.seller.facade

import com.aechak.application.seller.service.SellerService
import com.aechak.application.seller.usecase.SellerUseCase
import org.springframework.stereotype.Service

/**
 * SellerUseCase의 유일한 구현체. @Transactional 경계는 여기 고정.
 * 규칙은 user 도메인 템플릿(UserFacade) 참조.
 */
@Service
class SellerFacade(
    private val sellerService: SellerService,
) : SellerUseCase {
    // TODO: 유스케이스 구현
}
