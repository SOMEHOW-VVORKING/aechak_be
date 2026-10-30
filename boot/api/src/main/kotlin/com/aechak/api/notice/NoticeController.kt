package com.aechak.api.notice

import com.aechak.api.notice.response.NoticeDetailResponse
import com.aechak.api.notice.response.NoticeListResponse
import com.aechak.application.notice.read.usecase.NoticeReadUseCase
import com.aechak.application.notice.read.usecase.query.NoticeListQuery
import com.aechak.domain.notice.notice.enums.NoticeAudience
import com.aechak.domain.notice.notice.enums.NoticeCategory
import com.aechak.webcommon.response.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 구매자가 읽는 서비스 공지 — 운영자가 게시한 것 중 전체·구매자 대상만 내려온다.
 *
 * 노출 대상(BUYER)은 경로가 고정한다. 요청 파라미터로 받으면 구매자가 셀러 대상 공지를 요구할 수 있다.
 * 로그인은 요구하지만 사용자별로 갈리는 내용이 없어 프린시펄을 쓰지 않는다 —
 * 비회원 공개는 SecurityConfig의 permitAll 목록에 경로를 더하는 별도 판단이다.
 */
@RestController
@RequestMapping("/notices")
class NoticeController(
    private val noticeReadUseCase: NoticeReadUseCase,
) {
    /** 공지 목록 — 고정 공지 먼저, 그 다음 최근 게시순. 분류 필터와 커서 페이징. */
    @GetMapping
    fun getNotices(
        @RequestParam(required = false) category: NoticeCategory?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<NoticeListResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(
                NoticeListResponse.from(
                    noticeReadUseCase.list(NoticeAudience.BUYER, NoticeListQuery.of(category, cursor, size)),
                ),
            ),
        )

    /** 공지 한 건 — 전문. 게시되지 않았거나 구매자 대상이 아니면 150000이다. */
    @GetMapping("/{noticeId}")
    fun getNotice(
        @PathVariable noticeId: String,
    ): ResponseEntity<ApiResponse<NoticeDetailResponse>> =
        ResponseEntity.ok(
            ApiResponse.of(NoticeDetailResponse.from(noticeReadUseCase.detail(NoticeAudience.BUYER, noticeId))),
        )
}
