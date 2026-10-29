package com.aechak.application.order.claim.admin

import com.aechak.application.order.claim.admin.port.AdminClaimSort
import com.aechak.application.order.claim.admin.support.AdminClaimCursorCodec
import com.aechak.application.order.claim.admin.usecase.query.AdminClaimSearchQuery
import com.aechak.application.support.CursorPageSize
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ClaimType
import java.time.LocalDateTime
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 어드민 클레임 커서·검색 조건 조립. 커서가 정렬축을 들고 있어야 페이지 중간에 정렬을 바꾼 요청에서
 * 키셋 부등호와 정렬이 갈리지 않는다.
 */
class AdminClaimSearchQueryTest {
    @Test
    fun `커서를 왕복하면 정렬축과 마지막 식별자가 보존된다`() {
        val encoded = AdminClaimCursorCodec.encode(AdminClaimSort.LATEST_FIRST, lastClaimId = 42L)

        val decoded = AdminClaimCursorCodec.decode(encoded)

        assertEquals(AdminClaimSort.LATEST_FIRST, decoded.sort)
        assertEquals(42L, decoded.lastClaimId)
    }

    @Test
    fun `깨진 커서와 토큰 수가 다른 커서와 알 수 없는 정렬축은 모두 거절한다`() {
        assertCursorRejected { AdminClaimCursorCodec.decode("%%%broken%%%") }
        assertCursorRejected { AdminClaimCursorCodec.decode(encoded("ac:OLDEST_FIRST")) }
        assertCursorRejected { AdminClaimCursorCodec.decode(encoded("ac:NOPE:1")) }
        assertCursorRejected { AdminClaimCursorCodec.decode(encoded("ac:OLDEST_FIRST:abc")) }
        assertCursorRejected { AdminClaimCursorCodec.decode(encoded("zz:OLDEST_FIRST:1")) }
    }

    @Test
    fun `커서의 정렬축이 요청 파라미터를 덮는다`() {
        val cursor = AdminClaimCursorCodec.encode(AdminClaimSort.OLDEST_FIRST, lastClaimId = 10L)

        val query = AdminClaimSearchQuery(sort = AdminClaimSort.LATEST_FIRST, cursor = cursor)

        assertEquals(AdminClaimSort.OLDEST_FIRST, query.effectiveSort, "커서를 믿지 않으면 키셋 부등호가 정렬과 갈린다")
        assertEquals(10L, query.toCondition().lastClaimId)
    }

    @Test
    fun `기본 정렬은 오래된 분쟁 먼저이고 커서가 없으면 키셋을 걸지 않는다`() {
        val query = AdminClaimSearchQuery(claimTypes = listOf(ClaimType.RETURN))

        val condition = query.toCondition()

        assertEquals(AdminClaimSort.OLDEST_FIRST, condition.sort)
        assertNull(condition.lastClaimId)
        assertEquals(CursorPageSize.fetchLimit(CursorPageSize.DEFAULT), condition.limit, "다음 페이지 유무를 세려면 한 건 더 읽어야 한다")
    }

    @Test
    fun `축을 주지 않으면 개입 가능한 건으로 좁히고 상태를 명시하면 풀린다`() {
        assertTrue(AdminClaimSearchQuery().toCondition().openOnly, "아무 축도 없는 요청은 운영자의 작업 목록을 받아야 한다")
        assertFalse(
            AdminClaimSearchQuery(statuses = listOf(ClaimStatus.WITHDRAWN)).toCondition().openOnly,
            "상태를 명시한 요청은 종결된 이력을 보려는 것이다",
        )
        assertTrue(
            AdminClaimSearchQuery(statuses = listOf(ClaimStatus.WITHDRAWN), openOnly = true).toCondition().openOnly,
            "명시한 값이 파생값을 이겨야 한다",
        )
        assertFalse(AdminClaimSearchQuery(openOnly = false).toCondition().openOnly)
    }

    @Test
    fun `총건수 조건은 커서를 떼어 전체 범위를 센다`() {
        val cursor = AdminClaimCursorCodec.encode(AdminClaimSort.OLDEST_FIRST, lastClaimId = 10L)

        val query = AdminClaimSearchQuery(cursor = cursor)

        assertEquals(10L, query.toCondition().lastClaimId)
        assertNull(query.toCountCondition().lastClaimId, "커서가 남으면 총건수가 페이지를 넘길수록 줄어든다")
    }

    @Test
    fun `공백만 있는 검색어는 걸지 않는 것으로 본다`() {
        val condition = AdminClaimSearchQuery(sellerKeyword = "  ", buyerKeyword = "", orderPublicId = " ").toCondition()

        assertNull(condition.sellerKeyword)
        assertNull(condition.buyerKeyword)
        assertNull(condition.orderPublicId)
    }

    @Test
    fun `검색어 앞뒤 공백은 걷어낸다`() {
        val condition = AdminClaimSearchQuery(sellerKeyword = " 멍냥 ", buyerKeyword = " 코코 ").toCondition()

        assertEquals("멍냥", condition.sellerKeyword)
        assertEquals("코코", condition.buyerKeyword)
    }

    @Test
    fun `시작이 끝과 같거나 늦은 기간은 거절한다`() {
        val now = LocalDateTime.of(2026, 10, 20, 12, 0, 0)

        listOf(now to now, now.plusDays(1) to now).forEach { (from, to) ->
            val e = assertFailsWith<BusinessException> { AdminClaimSearchQuery(requestedFrom = from, requestedToExclusive = to) }
            assertEquals(CommonErrorCode.INVALID_REQUEST, e.errorCode)
        }
    }

    private fun encoded(payload: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray(Charsets.UTF_8))

    private fun assertCursorRejected(block: () -> Unit) {
        val e = assertFailsWith<BusinessException>(block = block)
        assertEquals(CommonErrorCode.INVALID_CURSOR, e.errorCode)
    }
}
