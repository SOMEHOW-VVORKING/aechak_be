package com.aechak.api.product.search

import com.aechak.api.product.response.ProductListResponse
import com.aechak.api.product.search.request.ProductKeywordSearchRequest
import com.aechak.application.product.search.usecase.ProductKeywordSearchUseCase
import com.aechak.application.search.usecase.SearchKeywordUseCase
import com.aechak.webcommon.response.ApiResponse
import com.aechak.websecurity.authentication.AuthPrincipal
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 키워드 상품 검색 API */
@RestController
@RequestMapping("/search")
class ProductKeywordSearchController(
    private val productKeywordSearchUseCase: ProductKeywordSearchUseCase,
    private val searchKeywordUseCase: SearchKeywordUseCase,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/products")
    fun searchProducts(
        @Valid @ModelAttribute request: ProductKeywordSearchRequest,
        @AuthenticationPrincipal principal: AuthPrincipal?,
    ): ResponseEntity<ApiResponse<ProductListResponse>> {
        val response = ProductListResponse.from(productKeywordSearchUseCase.searchProducts(request.toQuery()))
        // 첫 페이지(cursor 없음)만 적재. 커서 페이지 이동은 새 검색이 아님
        if (request.cursor == null) {
            recordRecentKeyword(principal, request.keyword)
        }
        return ResponseEntity.ok(ApiResponse.of(response))
    }

    /** 로그인 사용자만 최근 검색어 적재. 검색 응답 뒤 별도 트랜잭션, 실패는 트랜잭션 밖에서 Exception만 로깅 */
    private fun recordRecentKeyword(
        principal: AuthPrincipal?,
        keyword: String,
    ) {
        val userId = principal?.userId ?: return
        try {
            searchKeywordUseCase.recordRecentKeyword(userId, keyword)
        } catch (e: Exception) {
            log.warn("최근 검색어 적재 실패: userId={}", userId, e)
        }
    }
}
