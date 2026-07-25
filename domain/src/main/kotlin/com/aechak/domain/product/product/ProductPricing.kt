package com.aechak.domain.product.product

import java.time.LocalDateTime
import kotlin.math.roundToInt

/**
 * 상품 가격 계산 정책 — 정가·할인가·할인 기간으로 특정 시각의 유효가격을 결정한다.
 *
 * 등록 시점의 입력 불변식(할인가와 기간의 정합성)은 [Product.register]가 소유하며,
 * 이 클래스는 DB projection에서도 재구성되므로 생성자에 새 검증을 넣지 않는다(기존 데이터 조회가 깨진다).
 */
data class ProductPricing(
    val regularPrice: Long,
    val discountPrice: Long?,
    val discountStartAt: LocalDateTime?,
    val discountEndAt: LocalDateTime?,
) {
    /** 할인이 적용되는 기간이면 할인가 반환, 이외에는 null. 기간이 없는 경우 상시 할인으로 취급. */
    fun discountedPriceAt(at: LocalDateTime): Long? {
        val price = discountPrice ?: return null
        if (discountStartAt?.isAfter(at) == true) return null
        if (discountEndAt?.isBefore(at) == true) return null
        return price
    }

    /** 현재 판매 가격 */
    fun sellingPriceAt(at: LocalDateTime): Long = discountedPriceAt(at) ?: regularPrice

    /** 표시용 현재 할인율(%) */
    fun discountRateAt(at: LocalDateTime): Int? {
        val discounted = discountedPriceAt(at) ?: return null
        if (regularPrice == 0L) return null
        return ((regularPrice - discounted) * 100.0 / regularPrice).roundToInt()
    }
}
