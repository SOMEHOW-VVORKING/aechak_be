package com.aechak.domain.audit.auditlog.enums

/**
 * 감사 로그가 기록하는 운영 조치.
 *
 * 조치마다 대상 종류가 하나로 정해진다 — 같은 조치가 어떤 날은 상품, 어떤 날은 회원을 가리키면
 * 대상 종류 필터가 거짓을 말한다. 기록 시점에 둘의 짝을 대조한다.
 *
 * 조치는 **핸들러가 선언하는 값**이다(`@AdminAudited`). 그래서 요청 본문에 따라 세부가 갈리는
 * 조치는 한 값으로 들어온다 — 제재 네 종류(이용 제한·임시 정지·영구 정지·탈퇴 처리)가
 * [MEMBER_SANCTIONED] 하나다. 그 세부(종류·기간·메모)는 같은 대상 id로 묶이는 제재 이력
 * (`user_sanctions`, 추가 전용)이 들고 있고, 이 열거는 "무슨 성격의 조치였나"까지만 가른다.
 */
enum class AdminAuditAction(
    val targetType: AdminAuditTargetType,
    val label: String,
) {
    SELLER_APPLICATION_APPROVED(AdminAuditTargetType.SELLER_APPLICATION, "셀러 입점 승인"),
    SELLER_APPLICATION_REJECTED(AdminAuditTargetType.SELLER_APPLICATION, "셀러 입점 거부"),
    ADMIN_PROMOTED(AdminAuditTargetType.ADMIN_USER, "관리자 권한 부여"),
    ADMIN_DEMOTED(AdminAuditTargetType.ADMIN_USER, "관리자 권한 회수"),
    PRODUCT_INSPECTION_APPROVED(AdminAuditTargetType.PRODUCT, "상품 검수 승인"),
    PRODUCT_INSPECTION_REJECTED(AdminAuditTargetType.PRODUCT, "상품 검수 반려"),
    CLAIM_INTERVENED(AdminAuditTargetType.CLAIM, "클레임 개입"),
    MEMBER_SANCTIONED(AdminAuditTargetType.MEMBER, "회원 제재"),
    MEMBER_SANCTION_RELEASED(AdminAuditTargetType.MEMBER, "회원 제재 해제"),
    INQUIRY_ANSWERED(AdminAuditTargetType.INQUIRY, "서비스 문의 답변"),
    REVIEW_BLINDED(AdminAuditTargetType.REVIEW, "리뷰 블라인드"),
    REVIEW_UNBLINDED(AdminAuditTargetType.REVIEW, "리뷰 블라인드 해제"),
    REVIEW_DELETED(AdminAuditTargetType.REVIEW, "리뷰 삭제"),
    ORDER_DISPATCH_NUDGED(AdminAuditTargetType.ORDER, "셀러 발송 독촉"),
    REPORT_DECIDED(AdminAuditTargetType.REPORT, "신고 처리"),
}
