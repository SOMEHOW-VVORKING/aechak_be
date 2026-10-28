package com.aechak.application.order.claim.admin.port

/**
 * 어드민 클레임 목록 정렬축.
 *
 * 기본은 [OLDEST_FIRST] — 운영이 봐야 하는 것은 분쟁이 길어진 건이고, 그것이 가장 먼저 접수된 건이다.
 * 정렬·커서 키가 요청 시각이 아니라 클레임 id인 이유: id는 요청과 같은 트랜잭션에서 단조 증가로
 * 받으므로 요청 순서와 같은 순서이고, nullable인 requested_at으로 키셋을 짜면 값이 빈 행이
 * 두 번째 페이지부터 조용히 사라진다.
 */
enum class AdminClaimSort {
    OLDEST_FIRST,
    LATEST_FIRST,
}
