package com.aechak.domain.seller.seller.repository

import com.aechak.domain.seller.seller.Seller
import com.aechak.domain.seller.seller.enums.SellerStatus

interface SellerRepository {
    /** 기셀러 여부 — 입점 신청 전제 검증용(행 존재 = 셀러). */
    fun existsByUserId(userId: Long): Boolean

    fun save(seller: Seller): Seller

    /** 셀러 상태 — 셀러가 아니면 null. */
    fun findStatusByUserId(userId: Long): SellerStatus?

    /** 셀러 1건 — 셀러가 아니면 null. 수정 유스케이스가 로딩 후 변경하는 경로. */
    fun findByUserId(userId: Long): Seller?
}
