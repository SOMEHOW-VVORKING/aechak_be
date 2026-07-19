package com.aechak.application.auth.usecase.result

/**
 * 웹 로그인 진입 준비 결과 — 컨트롤러는 authorizeUrl로 302를, state로 브라우저 바인딩 쿠키를 만든다.
 * state를 따로 노출하는 이유: 쿠키 이중 제출(로그인 CSRF 방어)에 컨트롤러가 원문을 알아야 한다.
 */
data class WebLoginPreparation(
    val authorizeUrl: String,
    val state: String,
)
