package com.aechak.application.inspection.service

/**
 * 열린 검토 행이 들고 있는 이의 이력. 접수 자격을 가르는 입력이다.
 *
 * 행 자체를 넘기지 않는 이유는 변경자를 정책 쪽이 쥐지 않게 하려는 것이다. 행에 쓰는 길은
 * [ProductReviewQueueService]의 접수 경로 하나여야 한다.
 */
data class OpenAppealState(
    /** 지금 살아 있는 이의가 걸려 있는가. 참이면 중복 접수다 */
    val alreadyAppealed: Boolean,
    /** 이 행에 접수된 횟수. 무효화가 지우지 않아 "고치고 또 접수"의 분모가 된다 */
    val attemptCount: Int,
)
