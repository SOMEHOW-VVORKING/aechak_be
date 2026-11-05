package com.aechak.api.sns.controller

import com.aechak.api.sns.request.HashtagSearchRequest
import com.aechak.api.sns.response.HashtagSearchResponse
import com.aechak.application.sns.usecase.SnsPostUseCase
import com.aechak.webcommon.response.ApiResponse
import jakarta.validation.Valid
import org.springdoc.core.annotations.ParameterObject
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/sns/hashtags")
class HashtagController(
    private val snsPostUseCase: SnsPostUseCase,
) {
    // 결과가 보는 사람과 상관없어 principal을 받지 않는다. 로그인은 SecurityConfig가 요구한다
    @GetMapping
    fun searchHashtags(
        @Valid @ModelAttribute @ParameterObject request: HashtagSearchRequest,
    ): ResponseEntity<ApiResponse<HashtagSearchResponse>> =
        ResponseEntity.ok(ApiResponse.of(HashtagSearchResponse.from(snsPostUseCase.searchHashtags(request.query, request.size))))
}
