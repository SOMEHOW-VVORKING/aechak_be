package com.aechak.application.order.claim.admin.usecase.result

import com.aechak.application.support.CursorPageResult

/** 어드민 클레임 목록 한 페이지. 커서 페이징이라 총건수는 운영이 규모를 보는 참고값이다. */
data class AdminClaimPageResult(
    val page: CursorPageResult<AdminClaimSummaryResult>,
)
