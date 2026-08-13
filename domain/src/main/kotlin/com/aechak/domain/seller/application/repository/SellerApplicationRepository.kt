package com.aechak.domain.seller.application.repository

import com.aechak.domain.seller.application.SellerApplication

interface SellerApplicationRepository {
    fun save(application: SellerApplication): SellerApplication

    fun findById(id: Long): SellerApplication?

    /** 유저당 신청 1행(UNIQUE user_id) — 행 재사용 모델이라 단건이다. */
    fun findByUserId(userId: Long): SellerApplication?
}
