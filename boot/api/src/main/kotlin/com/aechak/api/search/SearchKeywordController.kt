package com.aechak.api.search

import com.aechak.api.search.response.SearchKeywordsResponse
import com.aechak.application.search.usecase.SearchKeywordUseCase
import com.aechak.webcommon.response.ApiResponse
import com.aechak.websecurity.authentication.AuthPrincipal
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/search")
class SearchKeywordController(
    private val searchKeywordUseCase: SearchKeywordUseCase,
) {
    @GetMapping("/keywords")
    fun getSearchKeywords(
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<SearchKeywordsResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(SearchKeywordsResponse.from(searchKeywordUseCase.getSearchKeywords(principal.userId))),
        )

    @DeleteMapping("/recent-keywords/{id}")
    fun deleteRecentKeyword(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<Void> {
        searchKeywordUseCase.deleteRecentKeyword(principal.userId, id)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/recent-keywords")
    fun deleteAllRecentKeywords(
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<Void> {
        searchKeywordUseCase.deleteAllRecentKeywords(principal.userId)
        return ResponseEntity.noContent().build()
    }
}
