package com.aechak.api.user.point

import com.aechak.api.user.point.response.PointBalanceResponse
import com.aechak.application.user.point.usecase.PointUseCase
import com.aechak.webcommon.response.ApiResponse
import com.aechak.websecurity.authentication.AuthPrincipal
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 적립금 API — 적립금 중 조회만 user 도메인 소관(적립·사용·복구는 주문/리뷰 도메인).
 * ACTIVE 전용 — 온보딩 화이트리스트 미포함이라 PENDING은 UserStatusFilter가 차단한다.
 */
@RestController
@RequestMapping("/users/me/points") // 접두(api.base-path)는 WebConfig가 일괄 부착
class PointController(
    private val pointUseCase: PointUseCase,
) {
    /** 내 적립금 잔액 — 마이·주문서 화면 표시용 단건(병렬 호출). */
    @GetMapping
    fun getMyPointBalance(
        @AuthenticationPrincipal principal: AuthPrincipal,
    ): ResponseEntity<ApiResponse<PointBalanceResponse>> =
        ResponseEntity.ok(ApiResponse.of(PointBalanceResponse.from(pointUseCase.getMyPointBalance(principal.userId))))
}
