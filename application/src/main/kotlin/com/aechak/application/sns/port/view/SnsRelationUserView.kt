package com.aechak.application.sns.port.view

import java.time.LocalDateTime

/** 관리 목록 행. relationId는 커서에만 쓴다 */
data class SnsRelationUserView(
    val relationId: Long,
    val publicId: String,
    val nickname: String,
    val profileImageKey: String?,
    val createdAt: LocalDateTime,
)
