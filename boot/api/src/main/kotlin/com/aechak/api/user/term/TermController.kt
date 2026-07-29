package com.aechak.api.user.term

import com.aechak.api.user.term.response.TermResponse
import com.aechak.application.user.term.usecase.ConsentUseCase
import com.aechak.webcommon.response.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/terms")
class TermController(
    private val consentUseCase: ConsentUseCase,
) {
    /** 활성 약관 전체 — 항목이 적어 본문까지 동봉한다(상세 EP 없음). PENDING 허용(화이트리스트). */
    @GetMapping
    fun getTerms(): ResponseEntity<ApiResponse<List<TermResponse>>> =
        ResponseEntity.ok(ApiResponse.of(consentUseCase.getActiveTerms().map(TermResponse::from)))
}
