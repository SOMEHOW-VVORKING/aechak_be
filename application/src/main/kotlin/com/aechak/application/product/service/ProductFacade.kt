package com.aechak.application.product.service

import com.aechak.application.product.usecase.ProductUseCase
import org.springframework.stereotype.Service

/**
 * ProductUseCase의 유일한 구현체. @Transactional 경계는 여기 고정.
 * 규칙은 user 도메인 템플릿(UserFacade) 참조.
 */
@Service
class ProductFacade(
    private val productService: ProductService,
) : ProductUseCase {
    // TODO: 유스케이스 구현
}
