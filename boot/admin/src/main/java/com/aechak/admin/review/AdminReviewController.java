package com.aechak.admin.review;

import com.aechak.admin.review.request.AdminReviewSearchRequest;
import com.aechak.admin.review.request.BlindReviewRequest;
import com.aechak.admin.review.request.DeleteReviewRequest;
import com.aechak.admin.review.response.AdminReviewDetailResponse;
import com.aechak.admin.review.response.AdminReviewListResponse;
import com.aechak.application.review.admin.usecase.AdminReviewUseCase;
import com.aechak.application.review.admin.usecase.command.BlindReviewCommand;
import com.aechak.application.review.admin.usecase.command.DeleteReviewCommand;
import com.aechak.application.review.admin.usecase.command.UnblindReviewCommand;
import com.aechak.webcommon.response.ApiResponse;
import com.aechak.websecurity.authentication.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 리뷰 관리 API. productId는 상품 공개 식별자(ULID)임.
 *
 * <p>구매자 조회와 달리 노출 상태로 걸러내지 않는다 — 가려진 리뷰를 되돌릴 수 있어야 하는 쪽이라
 * 블라인드·삭제된 리뷰도 목록과 상세에 나온다.
 */
@RestController
@RequestMapping("/admin/reviews")
@RequiredArgsConstructor
public class AdminReviewController {

    private final AdminReviewUseCase adminReviewUseCase;

    @GetMapping
    public ResponseEntity<ApiResponse<AdminReviewListResponse>> list(
            @Valid @ModelAttribute AdminReviewSearchRequest request) {
        return ResponseEntity.ok(
                ApiResponse.Companion.of(AdminReviewListResponse.from(adminReviewUseCase.list(request.toQuery()))));
    }

    @GetMapping("/{reviewId}")
    public ResponseEntity<ApiResponse<AdminReviewDetailResponse>> detail(@PathVariable long reviewId) {
        return ResponseEntity.ok(
                ApiResponse.Companion.of(AdminReviewDetailResponse.from(adminReviewUseCase.detail(reviewId))));
    }

    /** 이미 블라인드된 리뷰면 409(110020). 삭제된 리뷰는 400(110002). */
    @PostMapping("/{reviewId}/blind")
    public ResponseEntity<Void> blind(
            @PathVariable long reviewId,
            @Valid @RequestBody BlindReviewRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        adminReviewUseCase.blind(
                new BlindReviewCommand(principal.getUserId(), reviewId, request.reasonCode(), request.reasonText()));
        return ResponseEntity.noContent().build();
    }

    /** 블라인드가 아닌 리뷰면 409(110021). */
    @PostMapping("/{reviewId}/unblind")
    public ResponseEntity<Void> unblind(@PathVariable long reviewId, @AuthenticationPrincipal AuthPrincipal principal) {
        adminReviewUseCase.unblind(new UnblindReviewCommand(principal.getUserId(), reviewId));
        return ResponseEntity.noContent().build();
    }

    /** 되돌리는 경로가 없는 종결. 블라인드된 리뷰도 바로 삭제할 수 있다. */
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> delete(
            @PathVariable long reviewId,
            @Valid @RequestBody DeleteReviewRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        adminReviewUseCase.delete(
                new DeleteReviewCommand(principal.getUserId(), reviewId, request.reasonCode(), request.reasonText()));
        return ResponseEntity.noContent().build();
    }
}
