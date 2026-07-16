package com.aechak.application.auth.service

import com.aechak.application.auth.error.AuthErrorCode
import com.aechak.application.auth.port.RefreshTokenClaims
import com.aechak.application.auth.port.RefreshTokenEntry
import com.aechak.application.auth.port.RefreshTokenRef
import com.aechak.application.auth.port.RefreshTokenStore
import com.aechak.application.auth.port.TokenCodec
import com.aechak.application.auth.port.UserStatusReader
import com.aechak.common.error.BusinessException
import com.aechak.domain.user.user.enums.UserRole
import com.aechak.domain.user.user.enums.UserStatus
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 회전(rotation)·유예 멱등·재사용 감지 — 포트 뒤 유스케이스 로직 검증(포트 적용 기준의 근거). */
class TokenServiceTest {

    private val store = FakeRefreshTokenStore()
    private val statusReader = FakeUserStatusReader()
    private val service = TokenService(
        tokenCodec = FakeTokenCodec(),
        refreshTokenStore = store,
        userStatusReader = statusReader,
        policy = TokenPolicy(
            accessTtl = Duration.ofMinutes(30),
            refreshTtl = Duration.ofDays(30),
            rotationGrace = Duration.ofSeconds(60),
        ),
    )

    @Test
    fun `정상 회전 - 새 쌍이 나오고 옛 토큰은 successor를 기록한 유예 상태로 강등된다`() {
        val issued = service.issue(USER_ID, UserRole.GENERAL)

        val rotated = service.rotate(issued.refreshToken)

        assertNotEquals(issued.refreshTokenId, rotated.refreshTokenId)
        val oldEntry = store.find(USER_ID, issued.refreshTokenId)
        assertIs<RefreshTokenEntry.Rotated>(oldEntry)
        assertEquals(rotated.refreshTokenId, oldEntry.successor.tokenId)
        assertIs<RefreshTokenEntry.Active>(store.find(USER_ID, rotated.refreshTokenId))
    }

    @Test
    fun `유예 중 재제시 - 동일한 successor refresh token을 재반환한다(멱등)`() {
        val issued = service.issue(USER_ID, UserRole.GENERAL)
        val first = service.rotate(issued.refreshToken)

        val second = service.rotate(issued.refreshToken)

        assertEquals(first.refreshToken, second.refreshToken)
        assertEquals(first.refreshTokenId, second.refreshTokenId)
    }

    @Test
    fun `유예 경과 후 재제시 - 재사용으로 판정하고 전 세션을 무효화한다`() {
        val issued = service.issue(USER_ID, UserRole.GENERAL)
        val rotated = service.rotate(issued.refreshToken)
        store.expire(USER_ID, issued.refreshTokenId) // 유예 TTL 만료 시뮬레이션

        val exception = assertFailsWith<BusinessException> { service.rotate(issued.refreshToken) }

        assertEquals(AuthErrorCode.REFRESH_TOKEN_REUSED, exception.errorCode)
        assertNull(store.find(USER_ID, rotated.refreshTokenId)) // 살아 있던 세션까지 전부 삭제
    }

    @Test
    fun `위조 토큰 - 20003으로 거부한다`() {
        val exception = assertFailsWith<BusinessException> { service.rotate("garbage") }

        assertEquals(AuthErrorCode.INVALID_REFRESH_TOKEN, exception.errorCode)
    }

    @Test
    fun `로그아웃 - 해당 세션만 폐기되고 식별 불가 토큰은 조용히 무시된다(멱등)`() {
        val issued = service.issue(USER_ID, UserRole.GENERAL)

        service.revoke(issued.refreshToken)
        service.revoke("garbage") // no-throw

        assertNull(store.find(USER_ID, issued.refreshTokenId))
    }

    @Test
    fun `폐기 후 갱신 시도 - 재사용 감지로 처리된다`() {
        val issued = service.issue(USER_ID, UserRole.GENERAL)
        service.revoke(issued.refreshToken)

        val exception = assertFailsWith<BusinessException> { service.rotate(issued.refreshToken) }

        assertEquals(AuthErrorCode.REFRESH_TOKEN_REUSED, exception.errorCode)
    }

    @Test
    fun `정지 유저 - 회전 자체가 거부된다(토큰 수명 연장 차단)`() {
        val issued = service.issue(USER_ID, UserRole.GENERAL)
        statusReader.status = UserStatus.SUSPENDED

        val exception = assertFailsWith<BusinessException> { service.rotate(issued.refreshToken) }

        assertEquals(AuthErrorCode.ACCOUNT_BLOCKED, exception.errorCode)
    }

    @Test
    fun `탈퇴 직후 유저 없음 - 회전이 거부된다`() {
        val issued = service.issue(USER_ID, UserRole.GENERAL)
        statusReader.status = null

        val exception = assertFailsWith<BusinessException> { service.rotate(issued.refreshToken) }

        assertEquals(AuthErrorCode.ACCOUNT_BLOCKED, exception.errorCode)
    }

    @Test
    fun `발급 - refresh 해시가 저장되고 만료 정보가 응답에 실린다`() {
        val issued = service.issue(USER_ID, UserRole.GENERAL)

        assertIs<RefreshTokenEntry.Active>(store.find(USER_ID, issued.refreshTokenId))
        assertTrue(issued.expiresInSeconds > 0)
    }

    companion object {
        private const val USER_ID = 7L
    }
}

/** 상태를 갈아끼울 수 있는 가짜 리더 — 기본 ACTIVE. */
private class FakeUserStatusReader(var status: UserStatus? = UserStatus.ACTIVE) : UserStatusReader {
    override fun statusOf(userId: Long): UserStatus? = status
}

/** 결정적 인코딩을 흉내 내는 가짜 코덱 — 동일 클레임이면 동일 문자열(멱등 재발급 전제와 동일). */
private class FakeTokenCodec : TokenCodec {

    override fun encodeAccessToken(userId: Long, role: String, issuedAt: Instant, expiresAt: Instant): String =
        "access:$userId:$role:${issuedAt.epochSecond}:${expiresAt.epochSecond}"

    override fun encodeRefreshToken(
        userId: Long,
        role: String,
        tokenId: String,
        issuedAt: Instant,
        expiresAt: Instant,
    ): String = "refresh:$userId:$role:$tokenId:${issuedAt.epochSecond}:${expiresAt.epochSecond}"

    override fun decodeRefreshToken(token: String): RefreshTokenClaims? {
        val parts = token.split(":")
        if (parts.size != 6 || parts[0] != "refresh") return null
        val expiresAt = Instant.ofEpochSecond(parts[5].toLong())
        if (expiresAt.isBefore(Instant.now())) return null
        return RefreshTokenClaims(
            userId = parts[1].toLong(),
            role = parts[2],
            tokenId = parts[3],
            issuedAt = Instant.ofEpochSecond(parts[4].toLong()),
            expiresAt = expiresAt,
        )
    }
}

private class FakeRefreshTokenStore : RefreshTokenStore {

    private val entries = mutableMapOf<Pair<Long, String>, RefreshTokenEntry>()

    override fun save(userId: Long, tokenId: String, tokenHash: String, ttl: Duration) {
        entries[userId to tokenId] = RefreshTokenEntry.Active(tokenHash)
    }

    override fun find(userId: Long, tokenId: String): RefreshTokenEntry? = entries[userId to tokenId]

    override fun markRotated(userId: Long, tokenId: String, successor: RefreshTokenRef, grace: Duration) {
        entries[userId to tokenId] = RefreshTokenEntry.Rotated(successor)
    }

    override fun delete(userId: Long, tokenId: String) {
        entries.remove(userId to tokenId)
    }

    override fun deleteAll(userId: Long) {
        entries.keys.removeAll { it.first == userId }
    }

    /** TTL 만료 시뮬레이션 — 테스트 전용. */
    fun expire(userId: Long, tokenId: String) = delete(userId, tokenId)
}
