package com.aechak.domain.notice.notice.enums

/**
 * 공지 게시 상태.
 *
 * 예약 게시(SCHEDULED)를 두지 않는다 — 시각이 되면 PUBLISHED로 옮겨 줄 배치가 없어서,
 * 상태만 만들면 영원히 예약에 머무는 공지가 생긴다. 예약을 넣는 PR이 이 enum에 값을 더한다.
 */
enum class NoticeStatus {
    DRAFT, // 작성 중 — 읽는 쪽에 보이지 않는다
    PUBLISHED, // 게시 중
    UNPUBLISHED, // 내림 — 게시한 적이 있으나 지금은 보이지 않는다
}
