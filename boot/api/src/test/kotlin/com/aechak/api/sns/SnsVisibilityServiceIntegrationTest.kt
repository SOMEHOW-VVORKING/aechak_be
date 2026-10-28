package com.aechak.api.sns

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.sns.service.SnsVisibilityService
import com.aechak.domain.sns.block.repository.BlockRepository
import com.aechak.domain.sns.mute.repository.MuteRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * 통합: 가시성 판정이 MySQL의 차단과 뮤트 행을 방향대로 읽는지.
 * 깨지면 나를 차단한 사용자가 목록에 남거나 뮤트한 사용자가 차단 집합에 섞여 사라진다.
 */
class SnsVisibilityServiceIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var visibilityService: SnsVisibilityService

    @Autowired
    private lateinit var blockRepository: BlockRepository

    @Autowired
    private lateinit var muteRepository: MuteRepository

    @Test
    fun `차단 집합은 내가 건 차단과 나를 건 차단을 모두 담고 뮤트한 사용자는 담지 않는다`() {
        val me = createActiveUser()
        val blockedByMe = createActiveUser()
        val blockingMe = createActiveUser()
        val muted = createActiveUser()
        tx.execute {
            blockRepository.insertIfAbsent(me, blockedByMe)
            blockRepository.insertIfAbsent(blockingMe, me)
            muteRepository.insertIfAbsent(me, muted)
        }

        assertEquals(setOf(blockedByMe, blockingMe), visibilityService.findViewerFilter(me), "차단 집합은 두 방향 차단 상대만 담아야 한다")
    }

    @Test
    fun `뮤트 집합은 내가 뮤트한 사용자만 담는다`() {
        val me = createActiveUser()
        val muted = createActiveUser()
        val mutingMe = createActiveUser()
        val blocked = createActiveUser()
        tx.execute {
            muteRepository.insertIfAbsent(me, muted)
            muteRepository.insertIfAbsent(mutingMe, me)
            blockRepository.insertIfAbsent(me, blocked)
        }

        assertEquals(setOf(muted), visibilityService.findMutedIds(me), "뮤트 집합은 내가 뮤트한 상대만 담아야 한다")
    }

    @Test
    fun `차단과 뮤트가 없는 사용자와 비로그인은 두 집합이 비어 있다`() {
        val me = createActiveUser()

        assertTrue(visibilityService.findViewerFilter(me).isEmpty(), "관계가 없는 사용자의 차단 집합이 비어 있어야 한다")
        assertTrue(visibilityService.findMutedIds(me).isEmpty(), "관계가 없는 사용자의 뮤트 집합이 비어 있어야 한다")
        assertTrue(visibilityService.findViewerFilter(null).isEmpty(), "비로그인의 차단 집합이 비어 있어야 한다")
        assertTrue(visibilityService.findMutedIds(null).isEmpty(), "비로그인의 뮤트 집합이 비어 있어야 한다")
    }

    @Test
    fun `쌍 상태는 두 방향 차단과 내 뮤트를 함께 읽는다`() {
        val me = createActiveUser()
        val other = createActiveUser()
        tx.execute {
            blockRepository.insertIfAbsent(other, me)
            muteRepository.insertIfAbsent(me, other)
        }

        val pair = visibilityService.findPairState(me, other)

        assertTrue(pair.blockedByTarget, "상대가 건 차단이 읽혀야 한다")
        assertFalse(pair.blockedByMe, "내가 건 차단이 없으므로 false여야 한다")
        assertTrue(pair.mutedByMe, "내 뮤트가 읽혀야 한다")
        assertFalse(pair.following, "팔로우 기능 전이라 following은 false여야 한다")
        assertFalse(visibilityService.findPairState(null, other).blockedByTarget, "비로그인은 차단 상태가 없어야 한다")
    }
}
