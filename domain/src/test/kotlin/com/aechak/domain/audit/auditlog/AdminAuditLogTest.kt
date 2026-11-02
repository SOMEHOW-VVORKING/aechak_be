package com.aechak.domain.audit.auditlog

import com.aechak.domain.audit.auditlog.enums.AdminAuditAction
import com.aechak.domain.audit.auditlog.enums.AdminAuditResult
import com.aechak.domain.audit.auditlog.enums.AdminAuditTargetIdKind
import com.aechak.domain.audit.auditlog.enums.AdminAuditTargetType
import com.aechak.domain.support.Ulid
import java.lang.reflect.Modifier
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * [단위] 감사 행이 **만들어질 때** 막는 것들을 고정한다.
 *
 * 감사 로그는 깨져도 조용하다 — 조치는 성공하고 행만 틀리게 남는다. 그래서 틀릴 수 있는 자리를
 * 생성 시점에 전부 막아 둔다: 조치와 대상 종류의 짝, 대상 식별자의 종류, 운영자 없는 기록.
 *
 * 수정 불가도 여기서 고정한다. 이 애그리거트의 요지가 "고칠 수 없다"이므로, 세터가 생기는
 * 변경은 테스트에서 막혀야 한다.
 */
class AdminAuditLogTest {
    private val now: LocalDateTime = LocalDateTime.now()
    private val productPublicId = Ulid.generate()

    @Test
    fun `조치와 대상 종류의 짝이 어긋나면 만들어지지 않는다`() {
        val exception =
            assertFailsWith<IllegalArgumentException> {
                record(
                    action = AdminAuditAction.PRODUCT_INSPECTION_APPROVED,
                    targetType = AdminAuditTargetType.SELLER_APPLICATION,
                    targetId = "17",
                )
            }

        assertTrue(exception.message!!.contains("PRODUCT"), "어느 짝이 어긋났는지 메시지에 남아야 한다: ${exception.message}")
    }

    @Test
    fun `공개 식별자 대상에 내부 숫자 id를 적으려 하면 막힌다`() {
        assertFailsWith<IllegalArgumentException> {
            record(
                action = AdminAuditAction.PRODUCT_INSPECTION_APPROVED,
                targetType = AdminAuditTargetType.PRODUCT,
                targetId = "4071",
            )
        }
    }

    @Test
    fun `내부 숫자 id 대상에 ULID를 적으려 하면 막힌다`() {
        assertFailsWith<IllegalArgumentException> {
            record(
                action = AdminAuditAction.ADMIN_PROMOTED,
                targetType = AdminAuditTargetType.ADMIN_USER,
                targetId = productPublicId,
            )
        }
    }

    @Test
    fun `0과 음수 내부 id는 식별자로 받지 않는다`() {
        listOf("0", "-1", "", " ", "12a").forEach { bad ->
            assertFailsWith<IllegalArgumentException>("targetId=$bad 가 통과했다") {
                record(
                    action = AdminAuditAction.ADMIN_PROMOTED,
                    targetType = AdminAuditTargetType.ADMIN_USER,
                    targetId = bad,
                )
            }
        }
    }

    /** ULID의 Crockford Base32는 I·L·O·U를 쓰지 않는다 — 26자라고 다 통과하면 대조가 무의미하다 */
    @Test
    fun `길이만 맞고 글자가 ULID가 아니면 막힌다`() {
        assertFailsWith<IllegalArgumentException> {
            record(
                action = AdminAuditAction.PRODUCT_INSPECTION_APPROVED,
                targetType = AdminAuditTargetType.PRODUCT,
                targetId = "I".repeat(Ulid.LENGTH),
            )
        }
    }

    /**
     * 거부된 시도의 식별자는 요청이 들고 온 값이라 형식부터 틀려 있을 수 있다. 거기서 막으면
     * "누가 무엇을 하려다 거부당했나"의 행이 사라진다 — 종류 대조는 성공 행에만 걸린다.
     */
    @Test
    fun `거부된 시도는 형식이 틀린 식별자라도 행으로 남는다`() {
        listOf(
            AdminAuditAction.PRODUCT_INSPECTION_APPROVED to "4071",
            AdminAuditAction.ADMIN_DEMOTED to "0",
            AdminAuditAction.ADMIN_DEMOTED to "-5",
            AdminAuditAction.ADMIN_PROMOTED to "nonsense",
        ).forEach { (action, badId) ->
            val log =
                record(
                    action = action,
                    targetType = action.targetType,
                    targetId = badId,
                    result = AdminAuditResult.FAILURE,
                    httpStatus = 404,
                )

            assertEquals(badId, log.targetId, "거부된 시도의 식별자를 그대로 남기지 못했다: $badId")
            assertEquals(AdminAuditResult.FAILURE, log.result)
        }
    }

    /** 성공 행의 종류 대조는 그대로 남아야 한다 — 실패 행을 살리면서 같이 풀려서는 안 된다 */
    @Test
    fun `성공 행의 식별자 종류 대조는 그대로 막는다`() {
        assertFailsWith<IllegalArgumentException> {
            record(
                action = AdminAuditAction.PRODUCT_INSPECTION_APPROVED,
                targetType = AdminAuditTargetType.PRODUCT,
                targetId = "4071",
                result = AdminAuditResult.SUCCESS,
            )
        }
    }

    /**
     * 실패 행의 식별자는 길이 제한이 풀린 통로다 — 경로 변수로 아무리 긴 값이 와도 칼럼을
     * 넘겨 INSERT가 터지면 안 된다(그러면 행이 다시 사라진다).
     */
    @Test
    fun `저장 길이를 넘는 실패 행 식별자는 잘라서 남긴다`() {
        val log =
            record(
                action = AdminAuditAction.PRODUCT_INSPECTION_APPROVED,
                targetType = AdminAuditTargetType.PRODUCT,
                targetId = "9".repeat(AdminAuditLog.TARGET_ID_MAX + 80),
                result = AdminAuditResult.FAILURE,
                httpStatus = 404,
            )

        assertEquals(AdminAuditLog.TARGET_ID_MAX, log.targetId.length)
    }

    @Test
    fun `대상이 빈 문자열이면 결과와 무관하게 남기지 않는다`() {
        listOf(AdminAuditResult.SUCCESS, AdminAuditResult.FAILURE).forEach { result ->
            listOf("", "   ").forEach { blank ->
                assertFailsWith<IllegalArgumentException>("result=$result targetId=[$blank] 가 통과했다") {
                    record(
                        action = AdminAuditAction.ADMIN_PROMOTED,
                        targetType = AdminAuditTargetType.ADMIN_USER,
                        targetId = blank,
                        result = result,
                    )
                }
            }
        }
    }

    @Test
    fun `운영자를 알 수 없는 기록은 남기지 않는다`() {
        assertFailsWith<IllegalArgumentException> {
            record(
                actorUserId = 0L,
                action = AdminAuditAction.ADMIN_PROMOTED,
                targetType = AdminAuditTargetType.ADMIN_USER,
                targetId = "9",
            )
        }
    }

    /**
     * 사유가 길어 기록이 통째로 실패하면 조치는 성공했는데 흔적만 사라진다 — 잘라서라도 남긴다.
     */
    @Test
    fun `저장 길이를 넘는 사유와 대상 이름은 잘라서 남긴다`() {
        val log =
            record(
                action = AdminAuditAction.PRODUCT_INSPECTION_REJECTED,
                targetType = AdminAuditTargetType.PRODUCT,
                targetId = productPublicId,
                targetLabel = "상".repeat(AdminAuditLog.TARGET_LABEL_MAX + 40),
                reason = "사".repeat(AdminAuditLog.REASON_MAX + 120),
            )

        assertEquals(AdminAuditLog.TARGET_LABEL_MAX, log.targetLabel!!.length)
        assertEquals(AdminAuditLog.REASON_MAX, log.reason!!.length)
    }

    @Test
    fun `성공 기록은 받은 값을 그대로 들고 있다`() {
        val log =
            record(
                actorUserId = 42L,
                action = AdminAuditAction.PRODUCT_INSPECTION_REJECTED,
                targetType = AdminAuditTargetType.PRODUCT,
                targetId = productPublicId,
                targetLabel = "프리미엄 관절 영양제 180정",
                reason = "허위 광고 신고 7건 확인",
                result = AdminAuditResult.FAILURE,
                httpStatus = 409,
            )

        assertEquals(42L, log.actorUserId)
        assertEquals(productPublicId, log.targetId)
        assertEquals("프리미엄 관절 영양제 180정", log.targetLabel)
        assertEquals("허위 광고 신고 7건 확인", log.reason)
        assertEquals(AdminAuditResult.FAILURE, log.result)
        assertEquals(409, log.httpStatus)
        assertEquals(Ulid.LENGTH, log.publicId.length)
        assertTrue(AdminAuditTargetIdKind.PUBLIC_ULID.accepts(log.publicId), "채번한 publicId가 ULID 형태가 아니다")
    }

    /**
     * 고칠 수 없다는 것이 이 애그리거트의 전부다. 세터가 하나라도 생기면 그 보장이 사라지므로
     * 리플렉션으로 막아 둔다 — 코틀린 `val`을 `var`로 바꾸는 한 줄이 조용히 지나가지 않게 한다.
     */
    @Test
    fun `상태를 바꾸는 공개 메서드가 없다`() {
        val mutators =
            AdminAuditLog::class.java.declaredMethods
                .filter { Modifier.isPublic(it.modifiers) }
                .map { it.name }
                .filter { it.startsWith("set") || it in MUTATING_NAMES }

        assertTrue(mutators.isEmpty(), "감사 행에 수정 경로가 생겼다: $mutators")
    }

    private fun record(
        actorUserId: Long = 7L,
        action: AdminAuditAction,
        targetType: AdminAuditTargetType,
        targetId: String,
        targetLabel: String? = null,
        reason: String? = null,
        result: AdminAuditResult = AdminAuditResult.SUCCESS,
        httpStatus: Int = 204,
    ): AdminAuditLog =
        AdminAuditLog.record(
            actorUserId = actorUserId,
            action = action,
            targetType = targetType,
            targetId = targetId,
            targetLabel = targetLabel,
            reason = reason,
            result = result,
            httpStatus = httpStatus,
            requestId = "req-1",
            clientIp = "10.42.8.117",
            actedAt = now,
        )

    private companion object {
        val MUTATING_NAMES = setOf("update", "modify", "redact", "anonymize", "delete", "clear")
    }
}
