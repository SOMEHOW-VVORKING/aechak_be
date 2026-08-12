package com.aechak.application.product.product.port

import com.aechak.application.product.product.port.view.ProductOptionsView

/** 공개 상품 옵션 조회 포트 */
interface ProductOptionsQueryPort {
    fun findVisibleOptions(publicId: String): ProductOptionsView?
}
