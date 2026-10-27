package com.aechak.application.sns.service

import com.aechak.application.sns.port.SnsFollowDirection
import com.aechak.application.sns.port.SnsRelationKind
import com.aechak.application.sns.port.SnsUserListQueryPort
import com.aechak.application.sns.port.view.SnsFollowUserView
import com.aechak.application.sns.port.view.SnsRelationUserView
import com.aechak.application.sns.usecase.query.SnsFollowListQuery
import java.text.Normalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 계약: 정규화한 닉네임 검색어가 12자를 넘으면 목록 쿼리를 돌리지 않고 빈 페이지를 돌려주는지.
 * 깨지면 아무와도 맞을 수 없는 검색어로 매번 조인 쿼리가 나간다.
 */
class SnsUserListServiceTest {
    private val searchedNicknames = mutableListOf<String?>()
    private val port =
        object : SnsUserListQueryPort {
            override fun findRelationPage(
                ownerId: Long,
                kind: SnsRelationKind,
                lastRelationId: Long?,
                limit: Int,
            ): List<SnsRelationUserView> = emptyList()

            override fun countRelations(
                ownerId: Long,
                kind: SnsRelationKind,
            ): Long = 0

            override fun findFollowPage(
                ownerId: Long,
                direction: SnsFollowDirection,
                hiddenUserIds: Set<Long>,
                nickname: String?,
                lastFollowId: Long?,
                limit: Int,
            ): List<SnsFollowUserView> {
                searchedNicknames += nickname
                return emptyList()
            }
        }
    private val service = SnsUserListService(port)

    private fun page(nickname: String) =
        service.getFollowPage(
            SnsFollowListQuery(
                viewerId = 1L,
                ownerPublicId = "owner",
                direction = SnsFollowDirection.FOLLOWERS,
                cursor = null,
                rawNickname = nickname,
            ),
            ownerId = 2L,
            ownerPublicId = "owner",
            hiddenUserIds = emptySet(),
        )

    @Test
    fun `13자 검색어는 쿼리 없이 빈 페이지를 돌려준다`() {
        val result = page("가".repeat(13))

        assertTrue(searchedNicknames.isEmpty(), "12자를 넘는 검색어로 목록 쿼리를 돌리면 안 된다: $searchedNicknames")
        assertTrue(result.items.isEmpty(), "12자를 넘는 검색어는 빈 페이지여야 한다")
        assertFalse(result.hasNext, "12자를 넘는 검색어는 다음 페이지가 없어야 한다")
    }

    @Test
    fun `12자 검색어와 NFC로 12자가 되는 NFD 검색어는 쿼리를 돌린다`() {
        val nfc = "가".repeat(12)
        val nfd = Normalizer.normalize(nfc, Normalizer.Form.NFD)

        page(nfc)
        page(nfd)

        assertEquals(listOf<String?>(nfc, nfc), searchedNicknames, "12자 이하 검색어는 NFC로 정규화해 목록 쿼리를 돌려야 한다")
    }
}
