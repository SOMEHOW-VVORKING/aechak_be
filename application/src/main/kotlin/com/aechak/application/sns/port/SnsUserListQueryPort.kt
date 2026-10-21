package com.aechak.application.sns.port

import com.aechak.application.sns.port.view.SnsRelationUserView

/** SNS 관계 행을 users, user_profiles와 조인해 읽는 사용자 목록 조회. 탈퇴 사용자는 조인에서 빠진다 */
interface SnsUserListQueryPort {
    /** 내가 건 차단이나 뮤트를 관계 행 id 내림차순으로 lastRelationId 다음부터 limit개 */
    fun findRelationPage(
        ownerId: Long,
        kind: SnsRelationKind,
        lastRelationId: Long?,
        limit: Int,
    ): List<SnsRelationUserView>

    fun countRelations(
        ownerId: Long,
        kind: SnsRelationKind,
    ): Long
}
