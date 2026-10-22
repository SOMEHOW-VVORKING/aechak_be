package com.aechak.application.sns.service

import com.aechak.application.sns.port.SnsUserListQueryPort
import com.aechak.application.sns.port.view.SnsRelationUserView
import com.aechak.application.sns.support.RelationCursorCodec
import com.aechak.application.sns.usecase.query.SnsRelationListQuery
import com.aechak.application.support.CursorPageResult
import com.aechak.application.support.CursorPageSize
import org.springframework.stereotype.Service

@Service
class SnsUserListService(
    private val userListQueryPort: SnsUserListQueryPort,
) {
    /** 관리 목록은 보는 사람 기준 차단 숨김을 적용하지 않는다. 첫 페이지에만 totalCount를 싣는다 */
    fun getRelationPage(
        query: SnsRelationListQuery,
        ownerPublicId: String,
    ): CursorPageResult<SnsRelationUserView> {
        val lastRelationId = query.cursor?.let { RelationCursorCodec.decode(it, query.kind, ownerPublicId) }
        val fetched =
            userListQueryPort.findRelationPage(query.userId, query.kind, lastRelationId, CursorPageSize.fetchLimit(query.size))
        return CursorPageResult.of(
            fetched = fetched,
            size = query.size,
            totalCount = if (query.cursor == null) userListQueryPort.countRelations(query.userId, query.kind) else null,
        ) { last -> RelationCursorCodec.encode(query.kind, ownerPublicId, last.relationId) }
    }
}
