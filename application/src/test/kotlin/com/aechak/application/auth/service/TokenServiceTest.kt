package com.aechak.application.auth.service

import com.aechak.application.auth.error.AuthErrorCode
import com.aechak.application.auth.port.RefreshTokenClaims
import com.aechak.application.auth.port.RefreshTokenEntry
import com.aechak.application.auth.port.RefreshTokenRef
import com.aechak.application.auth.port.RefreshTokenStore
import com.aechak.application.auth.port.TokenCodec
import com.aechak.application.auth.port.UserRoleReader
import com.aechak.application.auth.port.UserStatusReader
import com.aechak.common.error.BusinessException
import com.aechak.domain.user.user.enums.UserRole
import com.aechak.domain.user.user.enums.UserStatus
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 회전(rotation)·유예 멱등·재사용 감지 — 포트 뒤 유스케이스 로직 검증(포트 적용 기준의 근거). */
class TokenServiceTest {
    private val store = FakeRefreshTokenStore()
    private val statusReader = FakeUserStatusReader()
    private val roleReader = FakeUserRoleReader()
    private val service =
        TokenService(
            tokenCodec = FakeTokenCodec(),
            refreshTokenStore = store,
            userStatusReader = statusReader,
            userRoleReader = roleReader,
            policy =
                TokenPolicy(
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
    fun `위조 토큰 - 20002로 거부한다`() {
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

    @Test
    fun `승격 후 회전 - 토큰에 박힌 역할이 아니라 현재 역할로 발급된다`() {
        val issued = service.issue(USER_ID, UserRole.GENERAL)
        roleReader.role = UserRole.ADMIN

        val rotated = service.rotate(issued.refreshToken)

        // 가짜 코덱이 역할을 문자열에 담으므로 발급된 역할을 그대로 들여다볼 수 있다.
        assertTrue(rotated.accessToken.contains(":ADMIN:"), rotated.accessToken)
    }

    @Test
    fun `강등 후 회전 - 회수된 권한이 다음 발급부터 사라진다`() {
        val issued = service.issue(USER_ID, UserRole.ADMIN)
        roleReader.role = UserRole.GENERAL

        val rotated = service.rotate(issued.refreshToken)

        // 이쪽이 보안상 중요한 방향이다 — 복사하면 회수가 refresh TTL(30일) 내내 반영되지 않는다.
        assertTrue(rotated.accessToken.contains(":GENERAL:"), rotated.accessToken)
    }

    @Test
    fun `유예 재시도 - refresh는 바이트까지 같고 access만 현재 역할로 갱신된다`() {
        val issued = service.issue(USER_ID, UserRole.GENERAL)
        val first = service.rotate(issued.refreshToken)
        roleReader.role = UserRole.ADMIN

        val second = service.rotate(issued.refreshToken)

        // refresh는 저장된 successor 클레임으로 재현하는 것이라 입력이 하나라도 흔들리면 멱등이 깨진다.
        assertEquals(first.refreshToken, second.refreshToken)
        // access는 발급 시각이 매번 달라 애초에 멱등이 아니므로 현재 역할을 싣는다.
        assertTrue(second.accessToken.contains(":ADMIN:"), second.accessToken)
    }

    @Test
    fun `회전 직전에 유저가 사라진 경합 - 토큰의 역할로 되돌아가지 않고 거부한다`() {
        val issued = service.issue(USER_ID, UserRole.ADMIN)
        // 상태 검증은 통과하는데 역할 조회에서 사라진 상태 — 그 틈에 토큰 역할을 쓰면
        // 삭제된 계정의 권한이 되살아난다.
        roleReader.role = null

        val exception = assertFailsWith<BusinessException> { service.rotate(issued.refreshToken) }

        assertEquals(AuthErrorCode.ACCOUNT_BLOCKED, exception.errorCode)
    }

    @Test
    fun `역할이 첫 회전 전에 바뀐 유예 재시도 - 돌려받은 refresh로 다음 회전이 된다`() {
        val issued = service.issue(USER_ID, UserRole.GENERAL)
        // 승격을 첫 회전 '전'에 넣는다 — 위 테스트는 회전 뒤에 넣어 반대 순서만 덮는다.
        roleReader.role = UserRole.ADMIN

        val first = service.rotate(issued.refreshToken)
        val retried = service.rotate(issued.refreshToken) // 회전 응답 유실 → 유예 안에 재시도

        // 재시도로 돌려준 refresh가 Active 엔트리에 저장된 해시의 원문이어야 한다.
        assertEquals(first.refreshToken, retried.refreshToken)

        // 그 토큰으로 다음 회전이 통과한다 — 해시가 어긋나면 reportReuse가 전 세션을 지운다.
        val next = service.rotate(retried.refreshToken)

        assertIs<RefreshTokenEntry.Active>(store.find(USER_ID, next.refreshTokenId))
    }

    @Test
    fun `refresh에는 역할이 실리지 않는다 - 역할만 다른 두 회전이 같은 토큰을 만든다`() {
        val general = service.issue(USER_ID, UserRole.GENERAL)
        roleReader.role = UserRole.ADMIN

        val rotated = service.rotate(general.refreshToken)

        // 역할이 서명 입력에 남아 있으면 이 단언이 깨진다 — 유예 재현이 성립하는 근거다.
        assertFalse(rotated.refreshToken.contains("ADMIN"), rotated.refreshToken)
        assertFalse(rotated.refreshToken.contains("GENERAL"), rotated.refreshToken)
    }

    @Test
    fun `jti는 맞고 원문이 다른 토큰 - 해시 대조에서 재사용으로 판정한다`() {
        val issued = service.issue(USER_ID, UserRole.GENERAL)
        // 저장소에는 해시만 남는다. jti를 알아낸 공격자가 원문 없이 통과하지 못해야 한다.
        val forged = withShiftedIssuedAt(issued.refreshToken)

        val exception = assertFailsWith<BusinessException> { service.rotate(forged) }

        assertEquals(AuthErrorCode.REFRESH_TOKEN_REUSED, exception.errorCode)
        assertNull(store.find(USER_ID, issued.refreshTokenId))
    }

    /** jti는 그대로 두고 발급 시각만 1초 당겨 바이트만 다른 토큰을 만든다(가짜 코덱 형식에 의존). */
    private fun withShiftedIssuedAt(refreshToken: String): String {
        val parts = refreshToken.split(":")
        return (parts.take(3) + (parts[3].toLong() - 1).toString() + parts.drop(4)).joinToString(":")
    }

    companion object {
        private const val USER_ID = 7L
    }
}

/** 역할을 갈아끼울 수 있는 가짜 리더 — 기본 GENERAL. null은 유저 없음. */
private class FakeUserRoleReader(
    var role: UserRole? = UserRole.GENERAL,
) : UserRoleReader {
    override fun roleOf(userId: Long): UserRole? = role
}

/** 상태를 갈아끼울 수 있는 가짜 리더 — 기본 ACTIVE. */
private class FakeUserStatusReader(
    var status: UserStatus? = UserStatus.ACTIVE,
) : UserStatusReader {
    override fun statusOf(userId: Long): UserStatus? = status
}

/** 결정적 인코딩을 흉내 내는 가짜 코덱 — 동일 클레임이면 동일 문자열(멱등 재발급 전제와 동일). */
private class FakeTokenCodec : TokenCodec {
    override fun encodeAccessToken(
        userId: Long,
        role: String,
        issuedAt: Instant,
        expiresAt: Instant,
    ): String = "access:$userId:$role:${issuedAt.epochSecond}:${expiresAt.epochSecond}"

    override fun encodeRefreshToken(
        userId: Long,
        tokenId: String,
        issuedAt: Instant,
        expiresAt: Instant,
    ): String = "refresh:$userId:$tokenId:${issuedAt.epochSecond}:${expiresAt.epochSecond}"

    override fun decodeRefreshToken(token: String): RefreshTokenClaims? {
        val parts = token.split(":")
        if (parts.size != 5 || parts[0] != "refresh") return null
        val expiresAt = Instant.ofEpochSecond(parts[4].toLong())
        if (expiresAt.isBefore(Instant.now())) return null
        return RefreshTokenClaims(
            userId = parts[1].toLong(),
            tokenId = parts[2],
            issuedAt = Instant.ofEpochSecond(parts[3].toLong()),
            expiresAt = expiresAt,
        )
    }
}

private class FakeRefreshTokenStore : RefreshTokenStore {
    private val entries = mutableMapOf<Pair<Long, String>, RefreshTokenEntry>()

    override fun save(
        userId: Long,
        tokenId: String,
        tokenHash: String,
        ttl: Duration,
    ) {
        entries[userId to tokenId] = RefreshTokenEntry.Active(tokenHash)
    }

    override fun find(
        userId: Long,
        tokenId: String,
    ): RefreshTokenEntry? = entries[userId to tokenId]

    override fun markRotated(
        userId: Long,
        tokenId: String,
        successor: RefreshTokenRef,
        grace: Duration,
    ) {
        entries[userId to tokenId] = RefreshTokenEntry.Rotated(successor)
    }

    override fun delete(
        userId: Long,
        tokenId: String,
    ) {
        entries.remove(userId to tokenId)
    }

    override fun deleteAll(userId: Long) {
        entries.keys.removeAll { it.first == userId }
    }

    /** TTL 만료 시뮬레이션 — 테스트 전용. */
    fun expire(
        userId: Long,
        tokenId: String,
    ) = delete(userId, tokenId)
}
