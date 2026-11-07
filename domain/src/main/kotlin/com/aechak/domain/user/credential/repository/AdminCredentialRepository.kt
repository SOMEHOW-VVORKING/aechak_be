package com.aechak.domain.user.credential.repository

import com.aechak.domain.user.credential.AdminCredential

interface AdminCredentialRepository {
    /** 로그인 아이디로 자격을 찾는다. 없으면 null — 호출부는 "아이디가 틀렸다"를 응답에 드러내지 않는다. */
    fun findByLoginId(loginId: String): AdminCredential?

    fun save(credential: AdminCredential): AdminCredential
}
