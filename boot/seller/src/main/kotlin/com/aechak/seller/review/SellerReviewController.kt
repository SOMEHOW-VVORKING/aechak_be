package com.aechak.seller.review

import com.aechak.application.review.seller.usecase.SellerReviewUseCase
import com.aechak.seller.review.request.ReviewReplyWriteRequest
import com.aechak.seller.review.request.SellerReviewSearchRequest
import com.aechak.seller.review.response.SellerReviewListResponse
import com.aechak.seller.review.response.SellerReviewReplyResponse
import com.aechak.webcommon.response.ApiResponse
import com.aechak.websecurity.authentication.AuthPrincipal
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 답글은 리뷰당 하나이므로 리뷰 식별자가 답글의 주소다 — 답글 id를 경로에 두지 않는다.
 * 그래서 수정·삭제는 화면이 답글 id를 들고 다니지 않아도 되고, 같은 요청을 다시 보내도 대상이 흔들리지 않는다.
 */
@RestController
@RequestMapping("/sellers/me/reviews")
class SellerReviewController(
    private val sellerReviewUseCase: SellerReviewUseCase,
) {
    /** 내 상품 리뷰 목록 — 상품·별점·답글 여부 필터 + 커서 페이지네이션. 별점 분포 요약은 첫 페이지에만 실린다. */
    @GetMapping
    fun getMyProductReviews(
        @Valid @ModelAttribute request: SellerReviewSearchRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<SellerReviewListResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(
                SellerReviewListResponse.from(sellerReviewUseCase.getMyProductReviews(request.toQuery(principal.userId))),
            ),
        )

    @PostMapping("/{reviewId}/reply")
    fun writeReply(
        @PathVariable reviewId: Long,
        @Valid @RequestBody request: ReviewReplyWriteRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<SellerReviewReplyResponse>> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(
                ApiResponse.of(
                    SellerReviewReplyResponse.from(sellerReviewUseCase.writeReply(request.toCommand(principal.userId, reviewId))),
                ),
            )

    @PutMapping("/{reviewId}/reply")
    fun editReply(
        @PathVariable reviewId: Long,
        @Valid @RequestBody request: ReviewReplyWriteRequest,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<SellerReviewReplyResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(
                SellerReviewReplyResponse.from(sellerReviewUseCase.editReply(request.toCommand(principal.userId, reviewId))),
            ),
        )

    @DeleteMapping("/{reviewId}/reply")
    fun deleteReply(
        @PathVariable reviewId: Long,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<Void> {
        sellerReviewUseCase.deleteReply(principal.userId, reviewId)
        return ResponseEntity.noContent().build()
    }
}
