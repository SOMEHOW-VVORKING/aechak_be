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
 * 회전 규칙:
 * - 회전 시 옛 토큰은 즉시 삭제하지 않고 successor 기록 + 60초 유예 강등.
 * - 유예 중 재제시 → successor 쌍을 동일하게 재생성해 반환(멱등 — 회전 응답 유실 대비).
 * - 서명·만료는 유효한데 스토어에 없거나 해시 불일치 = 폐기 토큰 재사용(탈취) → 전 세션 무효화.
 */
@Service
class TokenService(
    private val tokenCodec: TokenCodec,
    private val refreshTokenStore: RefreshTokenStore,
    private val userStatusReader: UserStatusReader,
    private val policy: TokenPolicy,
) {

    fun issue(userId: Long, role: UserRole): TokenResult =
        issueAt(userId, role.name, now()).result

    fun rotate(refreshToken: String): TokenResult {
        val claims = tokenCodec.decodeRefreshToken(refreshToken)
            ?: throw BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN)

        // 정지·탈퇴 유저는 회전 자체를 거부 — API 차단(필터)과 별개로 토큰 수명 연장을 막는다.
        when (userStatusReader.statusOf(claims.userId)) {
            UserStatus.SUSPENDED, UserStatus.WITHDRAWN, null ->
                throw BusinessException(AuthErrorCode.ACCOUNT_BLOCKED)
            else -> Unit
        }

        return when (val entry = refreshTokenStore.find(claims.userId, claims.tokenId)) {
            null -> reportReuse(claims.userId)
            is RefreshTokenEntry.Rotated -> reissueSuccessor(claims, entry.successor)
            is RefreshTokenEntry.Active ->
                if (entry.tokenHash != sha256(refreshToken)) reportReuse(claims.userId)
                else rotateActive(claims)
        }
    }

    /** 멱등 폐기 — 식별 불가 토큰(위조·만료)은 조용히 무시한다. */
    fun revoke(refreshToken: String) {
        val claims = tokenCodec.decodeRefreshToken(refreshToken) ?: return
        refreshTokenStore.delete(claims.userId, claims.tokenId)
    }

    /** 전 세션 무효화 — 탈퇴·제재 등에서 사용. */
    fun revokeAll(userId: Long) = refreshTokenStore.deleteAll(userId)

    private data class Issued(val result: TokenResult, val refreshRef: RefreshTokenRef)

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

    private fun rotateActive(claims: RefreshTokenClaims): TokenResult {
        val issued = issueAt(claims.userId, claims.role, now())
        refreshTokenStore.markRotated(claims.userId, claims.tokenId, issued.refreshRef, policy.rotationGrace)
        return issued.result
    }

    /** 유예 중 재제시 — successor를 동일 클레임으로 재생성(access만 새로 발급). */
    private fun reissueSuccessor(claims: RefreshTokenClaims, successor: RefreshTokenRef): TokenResult {
        val issuedAt = now()
        val accessToken = tokenCodec.encodeAccessToken(claims.userId, claims.role, issuedAt, issuedAt + policy.accessTtl)
        val refreshToken = tokenCodec.encodeRefreshToken(
            claims.userId, claims.role, successor.tokenId, successor.issuedAt, successor.expiresAt,
        )
        return TokenResult(accessToken, refreshToken, successor.tokenId, policy.accessTtl.seconds)
    }

    private fun reportReuse(userId: Long): Nothing {
        refreshTokenStore.deleteAll(userId)
        throw BusinessException(AuthErrorCode.REFRESH_TOKEN_REUSED)
    }

    /** JWT 시각 클레임은 초 정밀도 — 멱등 재생성이 성립하려면 발급 시각도 초로 자른다. */
    private fun now(): Instant = Instant.now().truncatedTo(ChronoUnit.SECONDS)

    private fun sha256(token: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
