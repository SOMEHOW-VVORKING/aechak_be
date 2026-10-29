package com.aechak.domain.audit.auditlog.enums

/**
 * 감사 로그가 기록하는 운영 조치.
 *
 * 조치마다 대상 종류가 하나로 정해진다 — 같은 조치가 어떤 날은 상품, 어떤 날은 회원을 가리키면
 * 대상 종류 필터가 거짓을 말한다. 기록 시점에 둘의 짝을 대조한다.
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
}
