package com.aechak.domain.notice.notice.enums

/**
 * 공지 분류.
 *
 * 어드민 화면과 셀러 화면이 서로 다른 어휘를 쓰고 있어 합집합 한 벌로 둔다 —
 * 어드민은 서비스·점검·이벤트·정책 넷, 셀러는 정책·시스템·정산·행사 넷을 보여 준다.
 * 셀러 화면의 '시스템'은 MAINTENANCE와 같은 것이라 따로 두지 않는다.
 */
enum class NoticeCategory {
    SERVICE, // 서비스 일반
    MAINTENANCE, // 점검·장애
    POLICY, // 정책 변경
    EVENT, // 이벤트·기획전
    SETTLEMENT, // 정산 (셀러 대상 공지에서 주로 쓴다)
}
