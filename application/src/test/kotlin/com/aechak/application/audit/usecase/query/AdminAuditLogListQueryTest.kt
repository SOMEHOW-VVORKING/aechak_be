package com.aechak.application.audit.usecase.query

import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.audit.auditlog.enums.AdminAuditAction
import com.aechak.domain.audit.auditlog.enums.AdminAuditTargetType
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * 계약: 잘못된 조회 조건을 조용히 고치지 않고 90001로 끊는지.
 *
 * 깨지면 감사 화면이 거짓을 말한다 — 뒤집힌 기간을 맞바꿔 주면 운영자가 본 기간과 응답의
 * 기간이 다르고, 짝이 안 맞는 조치·대상 조합은 언제나 0건이라 "그 조치가 없었다"로 읽힌다.
 */
class AdminAuditLogListQueryTest {
    private val day = LocalDateTime.of(2026, 10, 26, 0, 0)

    @Test
    fun `뒤집힌 기간은 90001로 거절한다`() {
        assertInvalid { AdminAuditLogListQuery(from = day.plusDays(1), to = day) }
    }

    @Test
    fun `같은 시각의 닫힌 구간은 받는다`() {
        val query = AdminAuditLogListQuery(from = day, to = day)

        assertEquals(day, query.from)
        assertEquals(day, query.to)
    }

    @Test
    fun `조치와 대상 종류의 짝이 어긋나면 90001로 거절한다`() {
        assertInvalid {
            AdminAuditLogListQuery(
                targetType = AdminAuditTargetType.PRODUCT,
                action = AdminAuditAction.ADMIN_PROMOTED,
            )
        }
    }

    @Test
    fun `짝이 맞는 조치와 대상 종류는 받는다`() {
        val query =
            AdminAuditLogListQuery(
                targetType = AdminAuditTargetType.PRODUCT,
                action = AdminAuditAction.PRODUCT_INSPECTION_REJECTED,
            )

        assertEquals(AdminAuditAction.PRODUCT_INSPECTION_REJECTED, query.action)
    }

    @Test
    fun `범위를 벗어난 size는 90001로 거절한다`() {
        listOf(0, -1, 101).forEach { size -> assertInvalid("size=$size 가 통과했다") { AdminAuditLogListQuery(size = size) } }
    }

    /**
     * `?actorPublicId=` 를 그대로 조건에 넣으면 `publicId = ''` 가 되어 늘 0건이다 —
     * 감사 화면에서 그 0건은 "조치가 없었다"로 읽힌다.
     */
    @Test
    fun `공백만 있는 운영자 식별자는 거르지 않는다`() {
        assertNull(AdminAuditLogListQuery(rawActorPublicId = "").actorPublicId)
        assertNull(AdminAuditLogListQuery(rawActorPublicId = "   ").actorPublicId)
        assertEquals(
            "01JD5G7HJK8M2N4PQ6RSTV9WXY",
            AdminAuditLogListQuery(rawActorPublicId = " 01JD5G7HJK8M2N4PQ6RSTV9WXY ").actorPublicId,
        )
    }

    @Test
    fun `공백만 있는 검색어는 거르지 않는다`() {
        assertNull(AdminAuditLogListQuery(rawKeyword = "   ").keyword)
        assertNull(AdminAuditLogListQuery(rawKeyword = "").keyword)
        assertEquals("보리펫샵", AdminAuditLogListQuery(rawKeyword = "  보리펫샵 ").keyword)
    }

    private fun assertInvalid(
        message: String = "잘못된 조건이 통과했다",
        block: () -> Unit,
    ) {
        val exception = assertFailsWith<BusinessException>(message, block)
        assertEquals(CommonErrorCode.INVALID_REQUEST, exception.errorCode, message)
    }
}
