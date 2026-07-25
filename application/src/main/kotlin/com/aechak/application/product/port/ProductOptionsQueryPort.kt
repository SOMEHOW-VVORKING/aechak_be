package com.aechak.application.product.port

import com.aechak.application.product.port.result.ProductOptionsView

/** 공개 상품 옵션 조회 포트 */
interface ProductOptionsQueryPort {
    fun findVisibleOptions(publicId: String): ProductOptionsView?
}
