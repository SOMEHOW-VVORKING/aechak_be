package com.aechak.application.audit.usecase.query

import com.aechak.application.support.CursorPageSize
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.audit.auditlog.enums.AdminAuditAction
import com.aechak.domain.audit.auditlog.enums.AdminAuditResult
import com.aechak.domain.audit.auditlog.enums.AdminAuditTargetType
import java.time.LocalDateTime

/**
 * 감사 로그 목록 입력.
 *
 * 잘못된 조건을 조용히 고치지 않고 90001(400)로 끊는다. 기간을 뒤집힌 채 받아 맞바꾸면 운영자가
 * 본 기간과 응답의 기간이 달라지고, 짝이 안 맞는 조치·대상 종류 조합은 언제나 0건이라
 * 빈 목록으로 돌려주면 "그 기간에 조치가 없었다"로 읽힌다 — 감사 화면에서 가장 위험한 거짓말이다.
 */
data class AdminAuditLogListQuery(
    val rawActorPublicId: String? = null,
    val targetType: AdminAuditTargetType? = null,
    val action: AdminAuditAction? = null,
    val result: AdminAuditResult? = null,
    val from: LocalDateTime? = null,
    val to: LocalDateTime? = null,
    val rawKeyword: String? = null,
    val cursor: String? = null,
    val size: Int = CursorPageSize.DEFAULT,
) {
    /**
     * trim한 운영자 공개 식별자. 비면 null이라 거르지 않는다.
     *
     * 빈 문자열을 그대로 두면 `?actorPublicId=`(화면이 입력을 지우면 흔히 그렇게 보낸다)가
     * `publicId = ''` 조건이 되어 늘 0건을 돌려준다 — 운영자에게는 "조치가 없었다"로 읽힌다.
     */
    val actorPublicId: String? = rawActorPublicId?.trim()?.ifEmpty { null }

    /** trim한 검색어. 비면 null이라 거르지 않는다 */
    val keyword: String? = rawKeyword?.trim()?.ifEmpty { null }

    init {
        if (size !in CursorPageSize.MIN..CursorPageSize.MAX) {
            invalid("size: ${CursorPageSize.MIN}~${CursorPageSize.MAX} 범위여야 합니다.")
        }
        if (from != null && to != null && from.isAfter(to)) {
            invalid("from: 조회 시작 시각이 종료 시각보다 늦을 수 없습니다.")
        }
        if (action != null && targetType != null && action.targetType != targetType) {
            invalid("action: $action 는 대상 종류 $targetType 에 속하지 않습니다.")
        }
    }

    private fun invalid(detail: String): Nothing = throw BusinessException(CommonErrorCode.INVALID_REQUEST, detail = detail)
}
