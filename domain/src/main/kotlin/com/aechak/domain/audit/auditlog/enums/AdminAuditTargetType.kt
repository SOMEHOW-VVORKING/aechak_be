package com.aechak.domain.audit.auditlog.enums

/**
 * 운영 조치가 가리키는 대상의 종류.
 *
 * 종류마다 **식별자 종류를 선언**한다. 규칙은 하나다 — 어드민 API가 그 대상을 가리키는 데 쓰는
 * 식별자를 그대로 적는다. 상품·클레임·주문은 어드민 경로가 공개 ULID로 가리키니 ULID이고, 입점 신청·
 * 관리자·회원·서비스 문의·리뷰는 내부 id로 가리키니 내부 id다.
 *
 * 공개 식별자로 통일하지 않은 이유는 **실패 행**이다. 공개 식별자는 애그리거트를 읽어야 나오고,
 * 거부된 시도는 바로 그 읽기나 그 다음 검증에서 끊긴다 — 통일하면 "누가 무엇을 하려다 거부당했나"의
 * 대상 칸이 비는 행이 생긴다. 요청이 들고 온 식별자는 조치가 실패해도 이미 손에 있다.
 * 감사 조회는 어드민 모듈 게이트(role=ADMIN) 뒤에만 있고, 그 게이트는 이미 같은 숫자 id를
 * 신청 상세·관리자 명부 응답으로 내주고 있어 새로 열리는 노출면이 없다.
 *
 * 선언을 두는 값은 [AdminAuditLog][com.aechak.domain.audit.auditlog.AdminAuditLog]가 기록 시점에
 * 대조한다 — **성공 행에만.** 거부된 시도는 요청이 들고 온 식별자를 그대로 적는데 그 값은
 * 형식부터 틀려 있을 수 있고, 거기서 막으면 남아야 할 실패 행이 사라진다.
 * 입점 신청에 publicId가 생겨 어드민 경로가 그걸로 바뀌면 여기 한 줄을 바꾸고,
 * 그 뒤로 숫자를 적고 성공하는 경로는 그 대조에서 막힌다.
 *
 * 기록 경로가 아직 없는 대상 종류는 선언하지 않는다. 값만 있고 쓰는 곳이 없는 열거는
 * 화면 필터에서 "조치가 한 번도 없었다"와 "아직 기록하지 않는다"를 구별할 수 없게 만든다.
 *
 * [ADMIN_USER]와 [MEMBER]는 둘 다 users.id 를 가리키지만 한 종류로 합치지 않는다 — 운영 화면이
 * 관리자 명부와 일반 회원을 다른 경로로 다루고(승격·강등 / 제재·해제), 종류를 합치면 "회원에게 한
 * 조치"만 보려는 필터가 관리자 권한 변경까지 끌고 온다. 같은 계정이 양쪽에 등장할 수는 있고,
 * 그때 그 계정의 조치 전부를 보려면 두 종류를 각각 묻는다.
 */
enum class AdminAuditTargetType(
    val idKind: AdminAuditTargetIdKind,
    val label: String,
) {
    SELLER_APPLICATION(AdminAuditTargetIdKind.INTERNAL_ID, "입점 신청"),
    ADMIN_USER(AdminAuditTargetIdKind.INTERNAL_ID, "관리자"),
    MEMBER(AdminAuditTargetIdKind.INTERNAL_ID, "회원"),
    PRODUCT(AdminAuditTargetIdKind.PUBLIC_ULID, "상품"),
    CLAIM(AdminAuditTargetIdKind.PUBLIC_ULID, "클레임"),
    INQUIRY(AdminAuditTargetIdKind.INTERNAL_ID, "서비스 문의"),
    REVIEW(AdminAuditTargetIdKind.INTERNAL_ID, "리뷰"),
    ORDER(AdminAuditTargetIdKind.PUBLIC_ULID, "주문"),
    REPORT(AdminAuditTargetIdKind.TYPED_INTERNAL_ID, "신고"),
}
