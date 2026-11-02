package com.aechak.domain.notice.notice.repository

import com.aechak.domain.notice.notice.Notice

interface NoticeRepository {
    fun save(notice: Notice): Notice

    fun findByPublicId(publicId: String): Notice?

    /** 쓰기 경로 전용 — 행을 잠가 읽는다. */
    fun findByPublicIdForUpdate(publicId: String): Notice?
}
