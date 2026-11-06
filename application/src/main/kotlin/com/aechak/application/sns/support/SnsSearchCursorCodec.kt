package com.aechak.application.sns.support

import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import java.security.MessageDigest
import java.util.Base64

/**
 * SNS 검색 목록의 커서. 페이로드는 태그로 시작하는 `:` 구분 값을 base64url(패딩 없음)로 감싼 값이고 사용자 id는 싣지 않는다.
 * 범위 해시는 검색 범위(검색어나 태그)로 만들어 다른 검색어의 커서를 거절한다.
 *  - 최신순 sl:{scopeHash}:{lastPostPublicId}
 *  - 인기순 sp:{scopeHash}:{epochMinute}:{offset}
 *  - 계정 su:{scopeHash}:{bucket}:{followerCount}:{lastUserPublicId}
 *  - 해시태그 sh:{scopeHash}:{postCount}:{lastName}
 */
object SnsSearchCursorCodec {
    private const val SEPARATOR = ":"
    private const val LATEST_TAG = "sl"
    private const val POPULAR_TAG = "sp"
    private const val USERS_TAG = "su"
    private const val HASHTAGS_TAG = "sh"

    /** 인기순 커서. epochMinute은 순위를 정한 분이고 offset은 그 순위에서 다음에 읽을 자리다 */
    data class PopularCursor(
        val epochMinute: Long,
        val offset: Int,
    )

    /** 계정 커서. bucket은 닉네임 일치 묶음(0 같음, 1 시작, 2 포함)이다 */
    data class UserCursor(
        val bucket: Int,
        val followerCount: Long,
        val lastPublicId: String,
    )

    data class HashtagCursor(
        val postCount: Int,
        val lastName: String,
    )

    /** 검색 범위의 해시. 인기순 순위 키에도 쓴다 */
    fun scopeHash(scope: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(scope.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    fun encodeLatest(
        scope: String,
        lastPostPublicId: String,
    ): String = encode(LATEST_TAG, scopeHash(scope), lastPostPublicId)

    fun decodeLatest(
        cursor: String,
        scope: String,
    ): String = decodeScoped(cursor, LATEST_TAG, scope, 1)[0].takeIf { it.isNotBlank() } ?: invalidCursor()

    fun encodePopular(
        scope: String,
        epochMinute: Long,
        offset: Int,
    ): String = encode(POPULAR_TAG, scopeHash(scope), epochMinute.toString(), offset.toString())

    fun decodePopular(
        cursor: String,
        scope: String,
    ): PopularCursor {
        val (epochMinute, offset) = decodeScoped(cursor, POPULAR_TAG, scope, 2)
        return PopularCursor(
            epochMinute = epochMinute.toLongOrNull()?.takeIf { it > 0 } ?: invalidCursor(),
            offset = offset.toIntOrNull()?.takeIf { it in 0 until PopularScore.MAX_RANK } ?: invalidCursor(),
        )
    }

    fun encodeUsers(
        scope: String,
        last: UserCursor,
    ): String = encode(USERS_TAG, scopeHash(scope), last.bucket.toString(), last.followerCount.toString(), last.lastPublicId)

    fun decodeUsers(
        cursor: String,
        scope: String,
    ): UserCursor {
        val (bucket, followerCount, lastPublicId) = decodeScoped(cursor, USERS_TAG, scope, 3)
        return UserCursor(
            bucket = bucket.toIntOrNull()?.takeIf { it in 0..2 } ?: invalidCursor(),
            followerCount = followerCount.toLongOrNull()?.takeIf { it >= 0 } ?: invalidCursor(),
            lastPublicId = lastPublicId.takeIf { it.isNotBlank() } ?: invalidCursor(),
        )
    }

    fun encodeHashtags(
        scope: String,
        last: HashtagCursor,
    ): String = encode(HASHTAGS_TAG, scopeHash(scope), last.postCount.toString(), last.lastName)

    fun decodeHashtags(
        cursor: String,
        scope: String,
    ): HashtagCursor {
        val (postCount, lastName) = decodeScoped(cursor, HASHTAGS_TAG, scope, 2)
        return HashtagCursor(
            postCount = postCount.toIntOrNull()?.takeIf { it > 0 } ?: invalidCursor(),
            lastName = lastName.takeIf { it.isNotBlank() } ?: invalidCursor(),
        )
    }

    private fun encode(
        tag: String,
        vararg values: String,
    ): String =
        Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString((listOf(tag) + values).joinToString(SEPARATOR).toByteArray(Charsets.UTF_8))

    /** 범위 해시 뒤의 값 목록. 형식, 태그, 값 개수, 범위 해시가 요청과 다르면 INVALID_CURSOR */
    private fun decodeScoped(
        cursor: String,
        tag: String,
        scope: String,
        valueCount: Int,
    ): List<String> {
        val parts =
            try {
                String(Base64.getUrlDecoder().decode(cursor), Charsets.UTF_8).split(SEPARATOR)
            } catch (e: IllegalArgumentException) {
                throw BusinessException(CommonErrorCode.INVALID_CURSOR, e)
            }
        if (parts.size != valueCount + 2 || parts[0] != tag || parts[1] != scopeHash(scope)) invalidCursor()
        return parts.drop(2)
    }

    private fun invalidCursor(): Nothing = throw BusinessException(CommonErrorCode.INVALID_CURSOR)
}
