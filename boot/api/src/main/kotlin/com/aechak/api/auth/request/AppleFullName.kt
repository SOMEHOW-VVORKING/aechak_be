package com.aechak.api.auth.request

/** 애플이 최초 인가 1회만 제공하는 이름 컴포넌트 — 첫 로그인 요청에만 실려 온다. */
data class AppleFullName(
    val familyName: String? = null,
    val givenName: String? = null,
) {
    fun formatted(): String? =
        listOfNotNull(familyName, givenName).joinToString(" ").ifBlank { null }
}
