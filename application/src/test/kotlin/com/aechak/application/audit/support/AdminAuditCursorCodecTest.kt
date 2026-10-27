package com.aechak.application.audit.support

import com.aechak.application.audit.port.AdminAuditLogCondition
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.audit.auditlog.enums.AdminAuditAction
import com.aechak.domain.audit.auditlog.enums.AdminAuditResult
import com.aechak.domain.audit.auditlog.enums.AdminAuditTargetType
import java.time.LocalDateTime
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * 계약: 감사 목록 커서가 왕복되고, **필터가 하나라도 다른** 요청의 커서를 90002로 거절하는지.
 *
 * 깨지면 조건을 바꾼 채 이전 커서로 넘어가 빠진 구간이 생긴다 — 감사 화면에서 그 구간은
 * "조치가 없었다"로 읽히고, 운영자는 그것이 빈 게 아니라 안 보이는 것임을 알 수 없다.
 */
class AdminAuditCursorCodecTest {
    private val base = AdminAuditLogCondition()

    @Test
    fun `커서를 왕복하면 마지막 행 id가 보존된다`() {
        val cursor = AdminAuditCursorCodec.encode(base, 904_12L)

        assertEquals(904_12L, AdminAuditCursorCodec.decode(cursor, base))
    }

    @Test
    fun `필터를 하나씩 바꾼 조건의 커서는 전부 거절한다`() {
        val cursor = AdminAuditCursorCodec.encode(base, 10L)
        val changed =
            listOf(
                base.copy(actorPublicId = "01JD5G7HJK8M2N4PQ6RSTV9WXY"),
                base.copy(targetType = AdminAuditTargetType.PRODUCT),
                base.copy(action = AdminAuditAction.ADMIN_PROMOTED),
                base.copy(result = AdminAuditResult.FAILURE),
                base.copy(from = LocalDateTime.of(2026, 10, 1, 0, 0)),
                base.copy(to = LocalDateTime.of(2026, 10, 31, 0, 0)),
                base.copy(keyword = "보리펫샵"),
            )

        changed.forEach { condition ->
            assertCursorRejected("$condition 로 바뀐 커서가 통과했다") { AdminAuditCursorCodec.decode(cursor, condition) }
        }
    }

    @Test
    fun `base64가 아닌 문자열과 조각 수가 다른 페이로드를 거절한다`() {
        assertCursorRejected("base64가 아닌 값이 통과했다") { AdminAuditCursorCodec.decode("!!!not-base64!!!", base) }
        assertCursorRejected("조각 하나짜리 페이로드가 통과했다") { AdminAuditCursorCodec.decode(encode("12"), base) }
        assertCursorRejected("조각 셋짜리 페이로드가 통과했다") { AdminAuditCursorCodec.decode(encode("12:hash:extra"), base) }
    }

    @Test
    fun `0과 음수와 숫자가 아닌 id를 거절한다`() {
        val hash = AdminAuditCursorCodec.encode(base, 1L).let { decode(it).substringAfter(':') }

        listOf("0", "-5", "abc", "").forEach { badId ->
            assertCursorRejected("id=$badId 가 통과했다") { AdminAuditCursorCodec.decode(encode("$badId:$hash"), base) }
        }
    }

    /** 범위 해시만 싣고 조건의 원문을 싣지 않는다 — 커서에 검색어·운영자 식별자가 드러나면 안 된다 */
    @Test
    fun `커서 페이로드에 조건 원문이 드러나지 않는다`() {
        val condition = base.copy(actorPublicId = "01JD5G7HJK8M2N4PQ6RSTV9WXY", keyword = "보리펫샵")

        val payload = decode(AdminAuditCursorCodec.encode(condition, 3L))

        assertFalse(payload.contains("01JD5G7HJK8M2N4PQ6RSTV9WXY"), "커서에 운영자 식별자가 드러났다: $payload")
        assertFalse(payload.contains("보리펫샵"), "커서에 검색어가 드러났다: $payload")
    }

    private fun encode(payload: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())

    private fun decode(cursor: String): String = String(Base64.getUrlDecoder().decode(cursor))

    private fun assertCursorRejected(
        message: String,
        block: () -> Unit,
    ) {
        val exception = assertFailsWith<BusinessException>(message, block)
        assertEquals(CommonErrorCode.INVALID_CURSOR, exception.errorCode, message)
    }
}
