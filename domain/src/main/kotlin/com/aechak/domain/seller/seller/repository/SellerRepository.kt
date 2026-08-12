package com.aechak.domain.seller.seller.repository

import com.aechak.domain.seller.seller.Seller

interface SellerRepository {
    /** 기셀러 여부 — 입점 신청 전제 검증용(행 존재 = 셀러). */
    fun existsByUserId(userId: Long): Boolean

    fun save(seller: Seller): Seller
}
