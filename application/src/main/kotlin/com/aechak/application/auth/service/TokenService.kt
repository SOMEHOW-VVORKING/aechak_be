package com.aechak.application.auth.service

import com.aechak.application.auth.error.AuthErrorCode
import com.aechak.application.auth.port.RefreshTokenClaims
import com.aechak.application.auth.port.RefreshTokenEntry
import com.aechak.application.auth.port.RefreshTokenRef
import com.aechak.application.auth.port.RefreshTokenStore
import com.aechak.application.auth.port.TokenCodec
import com.aechak.application.auth.port.UserStatusReader
import com.aechak.application.auth.usecase.result.TokenResult
import com.aechak.common.error.BusinessException
import com.aechak.domain.support.Ulid
import com.aechak.domain.user.user.enums.UserRole
import com.aechak.domain.user.user.enums.UserStatus
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 자체 토큰 발급·회전(rotation) 로직 보관함.
 *
 * [회전이란] refresh token은 1회용이다 — 갱신에 쓰는 순간 폐기되고 새것으로 교체된다(사슬).
 * 탈취범과 정상 유저가 같은 사슬을 쓰면 반드시 충돌이 나므로 "복제 사용"을 탐지할 수 있다.
 * 회전이 없으면 훔친 토큰을 30일 내내 조용히 같이 쓸 수 있고, 서버는 알아챌 방법이 없다.
 *
 * [유예(grace)란] 회전 응답이 네트워크에서 유실되면 정상 클라이언트가 옛 토큰으로 재시도한다.
 * 이를 탈취로 오판해 전체 로그아웃시키지 않도록, 옛 토큰을 즉시 삭제하지 않고
 * 60초간 "후속(successor) 안내판"으로 강등해 둔다 — 그 사이 재시도엔 같은 새 쌍을 돌려준다(멱등).
 */
@Service
class TokenService(
    private val tokenCodec: TokenCodec,
    private val refreshTokenStore: RefreshTokenStore,
    private val userStatusReader: UserStatusReader,
    private val policy: TokenPolicy,
) {

    /** 로그인 시 최초 발급 — 이 유저의 새 refresh 사슬이 여기서 시작된다. */
    fun issue(userId: Long, role: UserRole): TokenResult =
        issueAt(userId, role.name, now()).result

    /**
     * 회전: 제시된 refresh를 폐기하고 새 쌍을 내려준다.
     * 제시 토큰이 스토어에서 어떤 상태냐에 따라 세 갈래 — 각 갈래가 곧 하나의 시나리오다.
     */
    fun rotate(refreshToken: String): TokenResult {
        // 1차 관문: 우리가 서명했고 아직 만료 전인 refresh인가 (위조·만료·access 오제출 → 20003)
        val claims = tokenCodec.decodeRefreshToken(refreshToken)
            ?: throw BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN)

        // 2차 관문: 정지·탈퇴·삭제된 계정은 회전 자체를 거부 —
        // API 차단(상태검증 필터)과 별개로, 토큰 수명이 연장되는 것까지 봉쇄하는 두 번째 저지선.
        when (userStatusReader.statusOf(claims.userId)) {
            UserStatus.SUSPENDED, UserStatus.WITHDRAWN, null ->
                throw BusinessException(AuthErrorCode.ACCOUNT_BLOCKED)
            else -> Unit
        }

        return when (val entry = refreshTokenStore.find(claims.userId, claims.tokenId)) {
            // [시나리오 A — 탈취 의심] 서명은 유효한데 스토어에 흔적이 없다.
            // 정상 토큰이라면 현행(Active)이든 방금 회전됨(Rotated, 60초)이든 반드시 존재해야 한다.
            // 없다 = 폐기된 지 오래된 토큰의 재등장 = 이 사슬을 나 아닌 누군가 이미 진행시켰다는 뜻.
            // 누가 도둑인지 알 수 없으므로 전 세션을 끊고 재로그인을 강제한다.
            null -> reportReuse(claims.userId)

            // [시나리오 B — 정상 재시도] 방금 회전된 토큰이 유예 안에 다시 왔다.
            // 회전 응답이 유실돼 클라이언트가 재시도한 것 — 이미 발급된 successor 쌍을 그대로 다시 준다(멱등).
            is RefreshTokenEntry.Rotated -> reissueSuccessor(claims, entry.successor)

            // [시나리오 C — 정상 회전] 현행 토큰이 맞다.
            // 해시까지 대조하는 이유: jti(토큰 id)만 알아낸 공격자가 원문 없이 통과하지 못하게 하는 2중 확인.
            is RefreshTokenEntry.Active ->
                if (entry.tokenHash != sha256(refreshToken)) reportReuse(claims.userId)
                else rotateActive(claims)
        }
    }

    /** 멱등 폐기(로그아웃) — 식별 불가 토큰(위조·만료)은 조용히 무시한다. 이미 없어도 성공. */
    fun revoke(refreshToken: String) {
        val claims = tokenCodec.decodeRefreshToken(refreshToken) ?: return
        refreshTokenStore.delete(claims.userId, claims.tokenId)
    }

    /** 전 세션 무효화 — 탈퇴·제재 등에서 사용. */
    fun revokeAll(userId: Long) = refreshTokenStore.deleteAll(userId)

    private data class Issued(val result: TokenResult, val refreshRef: RefreshTokenRef)

    /**
     * 발급의 원자 단위: 쌍 생성 + refresh의 "해시만" 저장.
     * 원문을 저장하지 않는 이유: 스토어가 유출돼도 토큰을 재구성할 수 없게(비밀번호 해시와 같은 원리).
     */
    private fun issueAt(userId: Long, role: String, issuedAt: Instant): Issued {
        val tokenId = Ulid.generate()
        val refreshExpiresAt = issuedAt + policy.refreshTtl
        val accessToken = tokenCodec.encodeAccessToken(userId, role, issuedAt, issuedAt + policy.accessTtl)
        val refreshToken = tokenCodec.encodeRefreshToken(userId, role, tokenId, issuedAt, refreshExpiresAt)
        refreshTokenStore.save(userId, tokenId, sha256(refreshToken), policy.refreshTtl)
        return Issued(
            TokenResult(accessToken, refreshToken, tokenId, policy.accessTtl.seconds),
            RefreshTokenRef(tokenId, issuedAt, refreshExpiresAt),
        )
    }

    /**
     * 정상 회전: 새 쌍을 만들고, 옛 토큰은 삭제 대신 "successor 안내판 + 유예 TTL"로 강등한다.
     * 순서 주의 — 새것 저장이 먼저다: 강등 후 저장 전에 죽으면 유저의 유효 토큰이 하나도 안 남는다.
     */
    private fun rotateActive(claims: RefreshTokenClaims): TokenResult {
        val issued = issueAt(claims.userId, claims.role, now())
        refreshTokenStore.markRotated(claims.userId, claims.tokenId, issued.refreshRef, policy.rotationGrace)
        return issued.result
    }

    /**
     * 유예 재시도 응답: 안내판에 적힌 successor 클레임(jti·발급/만료 시각)을 그대로 다시 인코딩한다.
     * RS256 서명은 같은 입력에 항상 같은 출력(결정적)이라, 원문을 저장하지 않고도
     * 처음 발급됐던 것과 바이트까지 동일한 refresh가 재현된다. access는 단명이라 그냥 새로 굽는다.
     */
    private fun reissueSuccessor(claims: RefreshTokenClaims, successor: RefreshTokenRef): TokenResult {
        val issuedAt = now()
        val accessToken = tokenCodec.encodeAccessToken(claims.userId, claims.role, issuedAt, issuedAt + policy.accessTtl)
        val refreshToken = tokenCodec.encodeRefreshToken(
            claims.userId, claims.role, successor.tokenId, successor.issuedAt, successor.expiresAt,
        )
        return TokenResult(accessToken, refreshToken, successor.tokenId, policy.accessTtl.seconds)
    }

    /**
     * 재사용(탈취) 판정: 이 유저의 refresh를 전부 지워 도둑이 쥔 사슬까지 끊는다.
     * 정상 유저도 함께 로그아웃되지만, 그게 "도둑을 못 끊는 것"보다 낫다는 트레이드오프.
     */
    private fun reportReuse(userId: Long): Nothing {
        refreshTokenStore.deleteAll(userId)
        throw BusinessException(AuthErrorCode.REFRESH_TOKEN_REUSED)
    }

    /** JWT 시각 클레임은 초 단위 — 나노초를 잘라야 "동일 클레임 = 동일 토큰" 재현(유예 멱등)이 성립한다. */
    private fun now(): Instant = Instant.now().truncatedTo(ChronoUnit.SECONDS)

    private fun sha256(token: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
