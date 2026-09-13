package com.aechak.application.user.user.port

import com.aechak.application.user.user.port.view.UserAuthorView

interface UserAuthorQueryPort {
    /** 작성자 표시용 배치 조회 — 없는 id는 결과에서 빠진다. */
    fun findAuthorsByIds(ids: Collection<Long>): List<UserAuthorView>
}
