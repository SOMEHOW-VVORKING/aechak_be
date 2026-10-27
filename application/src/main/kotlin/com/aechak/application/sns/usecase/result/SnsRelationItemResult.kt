package com.aechak.application.sns.usecase.result

import com.aechak.application.sns.port.view.SnsRelationUserView
import java.time.LocalDateTime

/** 관리 목록 행. createdAt은 차단이나 뮤트한 시각이다 */
data class SnsRelationItemResult(
    val publicId: String,
    val nickname: String,
    val profileImageUrl: String?,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(
            view: SnsRelationUserView,
            profileImageUrl: String?,
        ): SnsRelationItemResult =
            SnsRelationItemResult(
                publicId = view.publicId,
                nickname = view.nickname,
                profileImageUrl = profileImageUrl,
                createdAt = view.createdAt,
            )
    }
}
