package com.aechak.seller.notice

import com.aechak.application.notice.read.usecase.query.NoticeListQuery
import com.aechak.application.notice.seller.usecase.SellerNoticeUseCase
import com.aechak.domain.notice.notice.enums.NoticeCategory
import com.aechak.seller.notice.response.SellerNoticeDetailResponse
import com.aechak.seller.notice.response.SellerNoticeListResponse
import com.aechak.webcommon.response.ApiResponse
import com.aechak.websecurity.authentication.AuthPrincipal
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 셀러가 읽는 서비스 공지 — 운영자가 게시한 것 중 전체·셀러 대상만 내려온다.
 *
 * 노출 대상을 요청 파라미터로 받지 않는다. 이 경로는 셀러 대상으로 고정이고,
 * 구매자 대상 공지는 구매자 호스트의 경로가 돌려준다.
 */
@RestController
@RequestMapping("/sellers/me/notices")
class SellerNoticeController(
    private val sellerNoticeUseCase: SellerNoticeUseCase,
) {
    /** 공지 목록 — 고정 공지 먼저, 그 다음 최근 게시순. 분류 필터와 커서 페이징. */
    @GetMapping
    fun getMyNotices(
        @RequestParam(required = false) category: NoticeCategory?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") size: Int,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<SellerNoticeListResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(
                SellerNoticeListResponse.from(
                    sellerNoticeUseCase.list(principal.userId, NoticeListQuery.of(category, cursor, size)),
                ),
            ),
        )

    /** 공지 한 건 — 전문. 게시되지 않았거나 셀러 대상이 아니면 150000이다. */
    @GetMapping("/{noticeId}")
    fun getMyNotice(
        @PathVariable noticeId: String,
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<SellerNoticeDetailResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(SellerNoticeDetailResponse.from(sellerNoticeUseCase.detail(principal.userId, noticeId))),
        )
}
