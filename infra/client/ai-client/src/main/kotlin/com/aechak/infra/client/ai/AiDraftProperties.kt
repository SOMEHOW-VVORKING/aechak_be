package com.aechak.infra.client.ai

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * AI 초안 서버 접속 설정. base-url 이나 shared-secret 이 비면 어댑터를 조립하지 않는다 —
 * 포트가 null 이 되어 파사드가 UNAVAILABLE 을 돌린다.
 */
@ConfigurationProperties("aechak.ai.draft")
data class AiDraftProperties(
    val baseUrl: String = "",
    /** AI 쪽 AECHAK_AI_DRAFT_SHARED_SECRET과 같은 값. 어긋나면 전건 403이다 */
    val sharedSecret: String = "",
    val connectTimeout: Duration = Duration.ofSeconds(2),
    /** AI 쪽 draft_timeout_seconds=25보다 넉넉히 — 그쪽이 먼저 끊고 사유를 적게 둔다 */
    val readTimeout: Duration = Duration.ofSeconds(30),
)
