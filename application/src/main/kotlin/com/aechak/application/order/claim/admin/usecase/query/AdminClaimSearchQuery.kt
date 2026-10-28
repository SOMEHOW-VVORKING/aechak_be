package com.aechak.application.order.claim.admin.usecase.query

import com.aechak.application.order.claim.admin.port.AdminClaimCondition
import com.aechak.application.order.claim.admin.port.AdminClaimSort
import com.aechak.application.order.claim.admin.support.AdminClaimCursorCodec
import com.aechak.application.support.CursorPageSize
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ClaimType
import java.time.LocalDateTime

/**
 * 어드민 클레임 검색 입력. 커서를 받으면 커서에 담긴 정렬축이 [sort]를 덮는다 —
 * 두 값이 어긋난 요청은 거절하지 않고 커서를 믿는다(키셋 부등호와 정렬이 갈리는 쪽이 더 위험하다).
 *
 * [openOnly]를 주지 않으면 **상태 필터가 없을 때만** 개입 가능한 건으로 좁힌다 — 아무 축도 주지 않은
 * 요청이 받아야 하는 것은 운영자의 작업 목록이고(상품 검수 큐와 같은 규약), 상태를 명시한 요청은
 * 종결된 이력을 보려는 것이다. 명시하면 그 값이 이긴다.
 */
data class AdminClaimSearchQuery(
    val claimTypes: List<ClaimType> = emptyList(),
    val statuses: List<ClaimStatus> = emptyList(),
    val requestedFrom: LocalDateTime? = null,
    val requestedToExclusive: LocalDateTime? = null,
    val orderPublicId: String? = null,
    val sellerKeyword: String? = null,
    val buyerKeyword: String? = null,
    val openOnly: Boolean? = null,
    val sort: AdminClaimSort = AdminClaimSort.OLDEST_FIRST,
    val cursor: String? = null,
    val size: Int = CursorPageSize.DEFAULT,
) {
    init {
        if (requestedFrom != null && requestedToExclusive != null && !requestedFrom.isBefore(requestedToExclusive)) {
            throw BusinessException(CommonErrorCode.INVALID_REQUEST)
        }
    }

    private val decoded = cursor?.let(AdminClaimCursorCodec::decode)

    val effectiveSort: AdminClaimSort = decoded?.sort ?: sort

    val effectiveOpenOnly: Boolean = openOnly ?: statuses.isEmpty()

    /** 다음 페이지 유무를 세려고 한 건 더 읽는다 — 잘라내기는 CursorPageResult가 한다. */
    fun toCondition(): AdminClaimCondition =
        AdminClaimCondition(
            claimTypes = claimTypes,
            statuses = statuses,
            requestedFrom = requestedFrom,
            requestedToExclusive = requestedToExclusive,
            orderPublicId = orderPublicId?.trim()?.takeIf { it.isNotEmpty() },
            sellerKeyword = sellerKeyword?.trim()?.takeIf { it.isNotEmpty() },
            buyerKeyword = buyerKeyword?.trim()?.takeIf { it.isNotEmpty() },
            openOnly = effectiveOpenOnly,
            sort = effectiveSort,
            lastClaimId = decoded?.lastClaimId,
            limit = CursorPageSize.fetchLimit(size),
        )

    /** 총건수 집계용 — 커서를 떼어 전체 조건으로 센다. */
    fun toCountCondition(): AdminClaimCondition = toCondition().copy(lastClaimId = null)
}
