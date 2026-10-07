package com.aechak.domain.seller.application.repository

import com.aechak.domain.seller.application.SellerApplication
import com.aechak.domain.seller.application.enums.ApplicationStatus

interface SellerApplicationRepository {
    fun save(application: SellerApplication): SellerApplication

    fun findById(id: Long): SellerApplication?

    /** 유저당 신청 1행(UNIQUE user_id) — 행 재사용 모델이라 단건이다. */
    fun findByUserId(userId: Long): SellerApplication?

    /** 어드민 목록 — status 미지정 시 전체, 제출일 내림차순(미제출 행은 뒤로). (status, submitted_at) 인덱스 전제. */
    fun findPage(
        status: ApplicationStatus?,
        page: Int,
        size: Int,
    ): List<SellerApplication>

    /** findPage와 같은 필터의 전체 행 수 — 어드민 목록 totalCount. */
    fun countAll(status: ApplicationStatus?): Long

    /** 동일 사업자번호의 신청 전부 — 어드민 세탁 대조 보조(자기 행 제외는 호출부 책임). */
    fun findAllByBusinessRegNo(businessRegNo: String): List<SellerApplication>
}
