package com.aechak.domain.notice.notice.enums

/**
 * 공지 노출 대상.
 *
 * 읽는 쪽은 자기 대상(BUYER 또는 SELLER)과 ALL만 본다 — 구매자가 셀러 공지를,
 * 셀러가 구매자 공지를 보지 못하게 하는 유일한 축이다.
 */
enum class NoticeAudience {
    ALL, // 전체
    BUYER, // 구매자만
    SELLER, // 셀러만
    ;

    /** 이 대상 값을 가진 독자가 읽을 수 있는 공지의 대상 값 — 자기 것과 전체. */
    fun visibleTo(): Set<NoticeAudience> = setOf(ALL, this)

    companion object {
        /** 독자 대상으로 쓸 수 없는 값(ALL)을 거른다 — ALL은 작성 대상일 뿐 독자 신분이 아니다. */
        val READER_AUDIENCES = setOf(BUYER, SELLER)
    }
}
