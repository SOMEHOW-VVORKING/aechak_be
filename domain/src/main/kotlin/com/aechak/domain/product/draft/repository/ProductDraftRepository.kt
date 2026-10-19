package com.aechak.domain.product.draft.repository

import com.aechak.domain.product.draft.ProductDraft
import java.time.LocalDateTime

/**
 * 상품 임시저장 저장소 포트.
 * savedAfter/savedBefore는 보관 기간 경계 시각이다 — 조회는 살아 있는 초안만 보고, 지난 초안은 저장 때 지워진다.
 */
interface ProductDraftRepository {
    fun save(draft: ProductDraft): ProductDraft

    fun findLive(
        publicId: String,
        sellerId: Long,
        savedAfter: LocalDateTime,
    ): ProductDraft?

    /** 최근 저장순 */
    fun findAllLive(
        sellerId: Long,
        savedAfter: LocalDateTime,
    ): List<ProductDraft>

    fun countLive(
        sellerId: Long,
        savedAfter: LocalDateTime,
    ): Long

    /** 삭제된 행 수 반환 */
    fun deleteLive(
        publicId: String,
        sellerId: Long,
        savedAfter: LocalDateTime,
    ): Int

    /** 보관 기간이 지난 초안 정리, 삭제된 행 수 반환 */
    fun deleteExpired(
        sellerId: Long,
        savedBefore: LocalDateTime,
    ): Int
}
