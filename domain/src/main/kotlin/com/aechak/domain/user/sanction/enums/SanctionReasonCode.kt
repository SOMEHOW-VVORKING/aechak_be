package com.aechak.domain.user.sanction.enums

/**
 * 제재 사유 고정 어휘. 자유 문자열로 받으면 같은 사유가 운영자마다 다른 문장으로 쌓여
 * 집계도, 분쟁 때 "같은 사유로 몇 번 제재했는가"도 답할 수 없다. 신고 사유(ReportReasonCode)와 같은 결정이다.
 * 어휘로 담기지 않는 맥락은 reason_detail 이 받는다.
 */
enum class SanctionReasonCode {
    /** 리뷰 욕설·비방 반복 */
    ABUSIVE_REVIEW,

    /** 채팅 분쟁 반복 */
    CHAT_DISPUTE,

    /** 허위 신고 반복 */
    FALSE_REPORT,

    /** 위 어휘로 담기지 않는 사유 — 상세 사유가 반드시 함께 온다 */
    OTHER,
}
