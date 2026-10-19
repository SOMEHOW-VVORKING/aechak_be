package com.aechak.application.order.claim.usecase.query

import com.aechak.application.order.claim.port.SellerClaimCondition
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.claim.enums.RefundStatus
import java.time.LocalDate

/**
 * 셀러 클레임 목록 조회 입력. 상태 필터는 서버의 축 어휘를 그대로 받는다 —
 * 화면의 단일 표시 상태로 접는 일은 프론트 몫이므로 서버가 그 어휘를 알 필요가 없다.
 * 비어 있는 필터는 그 축으로 걸지 않는다는 뜻이다.
 */
data class SellerClaimSearchQuery(
    val sellerId: Long,
    val claimTypes: List<ClaimType> = emptyList(),
    val statuses: List<ClaimStatus> = emptyList(),
    val refundStatuses: List<RefundStatus> = emptyList(),
    val requestedFrom: LocalDate? = null,
    val requestedTo: LocalDate? = null,
    val page: Int = 0,
    val size: Int = DEFAULT_SIZE,
) {
    init {
        require(page >= 0) { "page는 0 이상이어야 합니다." }
        require(size in SIZE_MIN..SIZE_MAX) { "size는 $SIZE_MIN~$SIZE_MAX 범위 안에 있어야 합니다." }
        if (requestedFrom != null && requestedTo != null) {
            require(!requestedFrom.isAfter(requestedTo)) { "요청일 필터의 시작이 끝보다 늦을 수 없습니다." }
        }
    }

    /** 요청일 필터는 날짜 단위 반개구간이다 — 종료일 당일을 포함하려면 다음 날 0시가 상한이 된다. */
    fun toCondition(): SellerClaimCondition =
        SellerClaimCondition(
            sellerId = sellerId,
            claimTypes = claimTypes,
            statuses = statuses,
            refundStatuses = refundStatuses,
            requestedFrom = requestedFrom?.atStartOfDay(),
            requestedToExclusive = requestedTo?.plusDays(1)?.atStartOfDay(),
            offset = page.toLong() * size,
            limit = size,
        )

    companion object {
        const val DEFAULT_SIZE = 20

        // @Range(min, max) 속성이 long이라 Long 유지, Int는 컴파일 불가
        const val SIZE_MIN = 1L
        const val SIZE_MAX = 100L
    }
}
