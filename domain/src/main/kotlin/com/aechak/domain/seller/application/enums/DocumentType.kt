package com.aechak.domain.seller.application.enums

/** 입점 심사 서류 종류 — label은 사용자 안내 문구(필수 서류 미비 메시지 등)에 쓴다. */
enum class DocumentType(
    val label: String,
) {
    ID_CARD("신분증"),
    BANKBOOK_COPY("통장 사본"),
    BUSINESS_REGISTRATION("사업자등록증"),
    TELESALES_REPORT("통신판매업 신고증"),
    CORP_REGISTER("법인 등기부등본"),
    CORP_SEAL("법인 인감증명서"),
}
