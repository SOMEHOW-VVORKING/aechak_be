package com.aechak.domain.sns.report.enums

/** SNS 신고 사유. 스팸/광고, 욕설/혐오 표현, 음란/부적절, 사기/허위, 기타 */
enum class PostReportReason {
    SPAM,
    ABUSE,
    INAPPROPRIATE,
    FRAUD,
    OTHER,
}
