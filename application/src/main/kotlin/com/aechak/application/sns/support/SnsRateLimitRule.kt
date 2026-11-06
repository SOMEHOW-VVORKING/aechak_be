package com.aechak.application.sns.support

import com.aechak.application.ratelimit.usecase.RateLimit
import com.aechak.application.ratelimit.usecase.RateLimitRule
import java.time.Duration

/** SNS 행동별 빈도 제한. 켜기와 끄기를 합쳐 세는 행동은 규칙 하나를 함께 쓴다 */
enum class SnsRateLimitRule(
    vararg limits: RateLimit,
) : RateLimitRule {
    POST_CREATE(perMinute(5), RateLimit(30, Duration.ofHours(1))),
    POST_UPDATE(perMinute(10)),
    FOLLOW(perMinute(30), RateLimit(300, Duration.ofDays(1))),
    UNFOLLOW(perMinute(30)),
    BLOCK(perMinute(30)),
    UNBLOCK(perMinute(30)),
    MUTE(perMinute(30)),
    UNMUTE(perMinute(30)),
    REPOST(perMinute(60)),
    SAVE(perMinute(60)),

    COMMENT_CREATE(perMinute(10), RateLimit(100, Duration.ofHours(1))),

    // 게시물과 댓글 좋아요를 함께 센다
    LIKE_ON(perMinute(60)),
    LIKE_OFF(perMinute(60)),
    ;

    override val key: String get() = "sns:$name"
    override val limits: List<RateLimit> = limits.toList()
}

private fun perMinute(limit: Int) = RateLimit(limit, Duration.ofMinutes(1))
