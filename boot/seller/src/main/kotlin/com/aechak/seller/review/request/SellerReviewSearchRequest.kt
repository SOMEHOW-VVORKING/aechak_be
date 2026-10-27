package com.aechak.seller.review.request

import com.aechak.application.review.seller.usecase.query.SellerReviewListQuery
import com.aechak.application.support.CursorPageSize
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.support.Ulid
import io.swagger.v3.oas.annotations.media.Schema
import org.hibernate.validator.constraints.Range

/**
 * 셀러 리뷰 목록 조회 파라미터. 화면의 필터 세 개(상품·별점·답글 여부)에 1:1로 대응한다.
 * 세 값 모두 미지정이 "전체"이고, 미지정과 빈 문자열을 같게 다루지 않는다 — 빈 값은 형식 오류다.
 */
data class SellerReviewSearchRequest(
    @field:Schema(description = "상품 공개 식별자 — 미지정이면 내 상품 전체")
    val productId: String? = null,
    @field:Schema(description = "별점 필터(1~5) — 미지정이면 전체")
    val rating: Int? = null,
    @field:Schema(description = "답글 여부 — true면 답글 완료, false면 답글 없음, 미지정이면 전체")
    val hasReply: Boolean? = null,
    val cursor: String? = null,
    @field:Range(
        min = CursorPageSize.MIN,
        max = CursorPageSize.MAX,
        message = "size는 {min}~{max} 사이여야 합니다.",
    )
    val size: Int = CursorPageSize.DEFAULT,
) {
    fun toQuery(sellerId: Long): SellerReviewListQuery {
        rating?.let {
            if (it !in RATING_RANGE) {
                throw BusinessException(CommonErrorCode.INVALID_REQUEST, detail = "rating: 1~5 사이여야 합니다.")
            }
        }
        return SellerReviewListQuery(
            sellerId = sellerId,
            productPublicId = productId?.trim()?.takeIf { it.isNotEmpty() }?.also(::requireUlid),
            rating = rating,
            hasReply = hasReply,
            cursor = cursor,
            size = size,
        )
    }

    /**
     * 형식을 여기서 거른다. 커서가 이 값을 그대로 담아 필터 변경을 판정하므로 커서 구분자가 섞인 값은
     * 커서를 만드는 자리에서 터진다 — 해석할 수 없는 값은 빈 목록이 아니라 형식 오류로 끊는 쪽이 맞다.
     */
    private fun requireUlid(value: String) {
        if (value.length != Ulid.LENGTH || value.any { it !in ULID_ALPHABET }) {
            throw BusinessException(CommonErrorCode.INVALID_REQUEST, detail = "productId: 상품 식별자 형식이 올바르지 않습니다.")
        }
    }

    private companion object {
        val RATING_RANGE = 1..5

        /** Crockford Base32 — ulid-creator가 대문자로 채번한다. */
        const val ULID_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    }
}
