package com.aechak.application.product.product.service

import com.aechak.domain.product.product.Product

/**
 * 상품 수정의 결과와 재검수 판단에 필요한 정보.
 *
 * [versionNo]가 null이면 수정 가능한 필드가 하나도 달라지지 않아 버전을 남기지 않았다는 뜻이다.
 * 그때는 판정 대상 본문도 그대로이므로 재검수를 요청하지 않는다.
 */
data class ProductUpdateOutcome(
    val product: Product,
    val versionNo: Int?,
)
