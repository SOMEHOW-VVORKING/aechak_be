package com.aechak.api.auth.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** 웹(서버 콜백) 로그인 설정(auth.web.*) — return 화이트리스트는 정확 일치로만 검증한다. */
@ConfigurationProperties("auth.web")
data class WebLoginProperties(
    val returnUrls: List<String> = emptyList(),
    /** provider에 등록하는 서버 콜백 주소의 밑동(…/auth/callback까지). */
    val callbackBaseUrl: String = "",
    val stateTtl: Duration = Duration.ofMinutes(5),
)
