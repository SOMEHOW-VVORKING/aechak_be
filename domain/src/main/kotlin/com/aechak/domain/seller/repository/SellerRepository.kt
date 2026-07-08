package com.aechak.domain.seller.repository

import com.aechak.domain.seller.Seller

/**
 * seller 애그리거트 저장 포트. 구현은 infra 어댑터가 담당한다.
 * 시그니처는 도메인 타입만 사용한다 — Spring 타입 노출 금지.
 */
interface SellerRepository {
    fun findById(id: Long): Seller?
    fun save(seller: Seller): Seller
}
