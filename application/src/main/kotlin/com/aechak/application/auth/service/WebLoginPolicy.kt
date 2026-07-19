package com.aechak.application.auth.service

import java.time.Duration

/**
 * 웹(서버 콜백) 로그인 정책 — 값은 실행 모듈 설정(auth.web.*)이 빈으로 공급한다.
 *
 * - allowedReturnUrls: 로그인 후 복귀를 허용하는 FE 주소 화이트리스트. **정확 일치**로만 통과 —
 *   검증 없는 return은 오픈 리다이렉트(우리 도메인이 임의 사이트로 튕겨주는 발사대) 취약점이 된다.
 * - callbackBaseUrl: provider에 등록하는 서버 콜백 주소의 밑동(…/auth/callback까지).
 */
data class WebLoginPolicy(
    val allowedReturnUrls: Set<String>,
    val callbackBaseUrl: String,
    val stateTtl: Duration,
)
