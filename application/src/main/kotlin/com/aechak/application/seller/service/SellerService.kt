package com.aechak.application.seller.service

import com.aechak.domain.seller.seller.repository.SellerRepository
import org.springframework.stereotype.Service

/**
 * seller 도메인 비즈니스 로직 보관함 — Facade에서만 호출된다.
 * 리포지토리는 domain의 포트(SellerRepository)를 주입받는다 — 어댑터가 생기는 시점에 연결.
 */
@Service
class SellerService(
    private val sellerRepository: SellerRepository,
) {
    fun isActive(userId: Long): Boolean = sellerRepository.existsActiveByUserId(userId)
}
